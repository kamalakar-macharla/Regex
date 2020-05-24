import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.logger.Logger
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
//  - Add Support for Docker Container

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
                            error("There are issues in the pipeline properties file content. More information available in the Job's log.")
                        }

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Execute Postman Test Suite') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                         //Check for test suite collection params
                         collectionFile = ''
                         if (params.CollectionFile) {
                            collectionFile = "${params.CollectionFile}"
                        }
                        //Check for environment variable
                        environmentVariable = ''
                        if (params.EnvironmentVariable) {
                            environmentVariable = "${params.EnvironmentVariable}"
                        }
                        //Check for number of iterations
                        iterationCount = ''
                        if (params.IterationCount) {
                            iterationCount = "${params.IterationCount}"
                        }
                        logger.info('**********************  Running collection file using Newman  ***********************')
                        if ("${environmentVariable}" == 'NoEnvironmentVariable') {
                            //use Newman to execute postman collections
                            sh "newman run CollectionFiles/${collectionFile} -n ${iterationCount} --reporters cli,junit,htmlextra --reporter-junit-export \"NewmanReport.xml\" --reporter-htmlextra-export \"NewmanReport.html\""
                        }
                        else {
                            sh "newman run CollectionFiles/${collectionFile} -e \"EnvironmentVariables/${environmentVariable}\" -n ${iterationCount} --reporters cli,junit,htmlextra --reporter-junit-export \"NewmanReport.xml\" --reporter-htmlextra-export \"NewmanReport.html\""
                        }

                        stagesExecutionTimeTracker.runUnitTestsStageEnd()

                    }
                }
            }
        }
        post {
            always {
                script {
                    //Report of the collection run to a JUnit compatible XML file
                    def testReportMask = 'NewmanReport.xml'
                    junit allowEmptyResults: true, testResults: testReportMask

                    // Push results to Xray
                    if (configuration.projectKey != null) {
                        step([$class: 'XrayImportBuilder', endpointName: '/junit', importFilePath: 'NewmanReport.xml', importToSameExecution: 'true', projectKey: "${configuration.projectKey}", serverInstance: '2edfe8ca-da03-40c0-9768-dc73a6cb1253'])
                        }

                    //Report of the collection run to HTML format
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: false,
                        reportDir: '',
                        reportFiles: 'NewmanReport.html',
                        reportName: 'HTML Report'
                        ])
                    archiveArtifacts artifacts: 'NewmanReport.html', allowEmptyArchive: true

                    // Send notifications that may include the above console log with gating messages.
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()

                    cleanWs()
                }
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
                name:'CollectionFile',
                choices: "${configuration.collectionFile}",
                description: 'Select which collection file you would like to run.')
            choice(
                name:'EnvironmentVariable',
                choices: "${configuration.environmentVariable}",
                description: 'Select which environment variable you would like to run.')
            choice(
                name:'IterationCount',
                choices: "${configuration.iterationCount}",
                description: 'Select number of iterations you would like to run.')
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console')
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.POSTMAN)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}
