import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.logger.Logger
import com.manulife.maven.MavenPropertiesCalalogBuilder
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader

// TODO:
// - Integrate with SonarQube
// - Add Support for Docker Containers
def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        stages {
            stage('Init') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker = new StagesExecutionTimeTracker()
                        stagesExecutionTimeTracker.initStageStart()

                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-tst.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        // Maven
                        mvnSettings = '-B -U '
                        if (pipelineParams.testRepository == null) {
                            pipelineParams.testSuiteDirName = '.'
                        }

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Resolve Dependencies & Build') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()

                        buildInfo = Artifactory.newBuildInfo()
                        buildInfo.env.capture = true

                        artifactoryMaven = Artifactory.newMavenBuild()
                        artifactoryMaven.tool = 'Maven 3.3.9'
                        artifactoryMaven.resolver releaseRepo: pipelineParams.releaseRepo, snapshotRepo: pipelineParams.snapshotRepo, server: artifactoryServer

                        dir(pipelineParams.testSuiteDirName) {
                            // Maven clean and build test suite
                            artifactoryMaven.run pom: 'pom.xml', goals: "${mvnSettings} clean install -DskipTests".toString(), buildInfo: buildInfo
                        }
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Execute Test Suite') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        buildInfo = Artifactory.newBuildInfo()
                        buildInfo.env.capture = true

                        artifactoryMaven = Artifactory.newMavenBuild()
                        artifactoryMaven.tool = 'Maven 3.3.9'
                        artifactoryMaven.resolver releaseRepo: pipelineParams.releaseRepo, snapshotRepo: pipelineParams.snapshotRepo, server: artifactoryServer
                        dir(pipelineParams.testSuiteDirName) {
                            //Check for env parameters and configure appropriate variables for maven execution

                            //Check for browser param
                            browser = ''
                            if (params.Browser) {
                                browser = "-Dbrowser=${params.Browser}"
                            }

                            //Check for test env param
                            testEnv = ''
                            if (params.TestEnvironment) {
                                testEnv = "-Denvironment=${params.TestEnvironment}"
                            }

                            //Check for test automation tag group
                            automationGroup = ''
                            if (params.AutomationGroup) {
                                if (configuration.cucumberBDD == 'true') {
                                    def prefix = "\'--tags "
                                    def suffix = "\'"
                                    automationGroup = "-Dcucumber.options=${prefix}${params.AutomationGroup}${suffix}"
                                }
                                else {
                                    automationGroup = "-Dautomation.group=${params.AutomationGroup}"
                                }
                            }

                            //Check for language param
                            language = ''
                            if (params.Language) {
                                language = "-Dlanguage=${params.Language}"
                            }

                            //Check for nonBrowserTest param
                            nonBrowserTest = ''
                            if (params.NonBrowserTest && params.NonBrowserTest == 'true') {
                                nonBrowserTest = "-DnonBrowserTest=${params.NonBrowserTest}"
                            }

                            // Run test suite
                            artifactoryMaven.run pom: 'pom.xml',
                                                 goals: "${mvnSettings} ${pipelineParams.mavenTestGoal} ${browser} ${testEnv} ${automationGroup} ${language} ${nonBrowserTest}".toString(),
                                                 buildInfo:

                            stagesExecutionTimeTracker.runUnitTestsStageEnd()
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    //Use cucumber reports plugin if running BDD version of execution project
                    if (configuration.cucumberBDD == 'true') {
                        cucumber fileIncludePattern: '**/test-output/cucumber-reports/**/CucumberTestReport.json'

						//Push results to XRay
                        if (configuration.projectKey != null) {
                            step([$class: 'XrayImportBuilder', endpointName: '/cucumber', importFilePath: 'test-output/cucumber-reports/recent-result/CucumberTestReport.json', serverInstance: '2edfe8ca-da03-40c0-9768-dc73a6cb1253', testEnvironments: params.TestEnvironment])
                        }
                    }
					else if (configuration.uftTest == 'true') {
                        def testReportMask = '**/test-output/testng-results_UFT.xml'
                        junit allowEmptyResults: true, testResults: testReportMask
                        archiveArtifacts artifacts: '**/test-output/ExtentReport/**/*.html', allowEmptyArchive: true                                                          
                       //Push results to XRay
                       if (configuration.projectKey != null) {
                            step([$class: 'XrayImportBuilder', endpointName: '/testng', importFilePath: '**/test-output/testng-results_UFT.xml', importToSameExecution: 'true', projectKey: "${configuration.projectKey}", serverInstance: '2edfe8ca-da03-40c0-9768-dc73a6cb1253'])
                       }
                    }
                    else {
                        // Only the latest matching report kind may have failures
                        // because jobs normally fail on test failures.  Therefore,
                        // check for the integration test report first and ignore
                        // the unit test report as the latter would have 100%
                        // success.
                        def testReportMask = '**/target/failsafe-reports/*.xml'
                        junit allowEmptyResults: true, testResults: testReportMask
                        archiveArtifacts artifacts: '**/test-output/ExtentReport/**/*.html', allowEmptyArchive: true
						//Push results to XRay
                        if (configuration.projectKey != null) {
						    step([$class: 'XrayImportBuilder', endpointName: '/testng', importFilePath: '**/target/failsafe-reports/testng-results.xml', importToSameExecution: 'true', projectKey: "${configuration.projectKey}", serverInstance: '2edfe8ca-da03-40c0-9768-dc73a6cb1253'])
                        }
                    }

                    // Send notifications that may include the above console log with gating messages.
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()
                }
                cleanWs()
            }
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '1'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
        triggers {
            gitlab(
                triggerOnPush: false,
                triggerOnMergeRequest: false,
                triggerOpenMergeRequestOnPush: 'never',
                triggerOnNoteRequest: true,
                noteRegex: 'Jenkins please retry a build',
                skipWorkInProgressMergeRequest: true,
                ciSkip: true,
                setBuildDescription: true,
                addNoteOnMergeRequest: true,
                addCiMessage: true,
                addVoteOnMergeRequest: true,
                acceptMergeRequestOnSuccess: false,
                branchFilterType: 'RegexBasedFilter',
                targetBranchRegex: '',
                secretToken: '')
        }
        parameters {
            choice(
                name:'Browser',
                choices: "${configuration.browsers}",
                description: 'Select which browser you would like the test suite to run in.')
            choice(
                name:'TestEnvironment',
                choices: "${configuration.testEnv}",
                description: 'Select which region URL you would like the test suite to run in.')
            choice(
                name:'AutomationGroup',
                choices: "${configuration.testGroup}",
                description: 'Select which test group you would like to run.')
            choice(
                name:'Language',
                choices: "${configuration.language}",
                description: 'Select which language you would like to run your tests with.')
            choice(
                name:'NonBrowserTest',
                choices: "${configuration.nonBrowserTest}",
                description: 'Select true if the tests require no browser instance.')
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console')
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addOptionalProperty('testRepository', 'Defaulting testRepository property to null', null)
    propertiesCatalog.addOptionalProperty('testBranch', 'Defaulting testBranch property to */master', '*/master')
    propertiesCatalog.addOptionalProperty('testSuiteDirName', 'Defaulting testSuiteDirName to testSuite', 'testSuite')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SELENIUM)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SELENIUM)
    MavenPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SELENIUM)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}
