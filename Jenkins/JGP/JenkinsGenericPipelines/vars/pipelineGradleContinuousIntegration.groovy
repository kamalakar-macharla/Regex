import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.blackduck.BlackduckPropertiesCalalogBuilder
import com.manulife.blackduck.BlackDuckRunner
import com.manulife.blackduck.BlackDuckResult
import com.manulife.fortify.FortifyPropertiesCalalogBuilder
import com.manulife.fortify.FortifyResult
import com.manulife.fortify.FortifyRunner
import com.manulife.gating.GatingReport
import com.manulife.git.GitPropertiesCatalogBuilder
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.gradle.GradlePropertiesCatalogBuilder
import com.manulife.gradle.BuildGradleFile
import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.snyk.SnykPropertiesCatalogBuilder
import com.manulife.snyk.SnykRunner
import com.manulife.sonarqube.SonarQubePropertiesCalalogBuilder
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.sonarqube.SonarQubeRunner
import com.manulife.sonarqube.SonarQubeUtils
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.Shell
import com.manulife.versioning.IncreasePatchVersion

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        tools {
            gradle 'Gradle-5.5'
            jdk "${configuration.latestJava == true ? 'JDK 11' : 'JDK 8u112'}"
        }
        stages {
            stage('Init') {
                steps {
                    script {
                        stagesExecutionTimeTracker = new StagesExecutionTimeTracker()
                        stagesExecutionTimeTracker.initStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.propagateAndroidSDKHome(this, pipelineParams.androidSdkHome)
                        Shell.trustZscalerInJava(this, unix)

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Gradle
                        gradleSettings  = ' '

                        if (pipelineParams.useGradleWrapper.toBoolean()) {
                          gradleScript = './gradlew'
                          gradleScript = gradleScript + (unix ? '' : '.bat')
                        }
                        else {
                          gradleScript = "${tool('Gradle-5.5')}/bin/gradle"
                        }

                        if (logger.level <= Level.DEBUG) {
                            gradleSettings += '--debug '
                        }

                        logger.debug("gradleScript: ${gradleScript}")
                        logger.debug("gradleSettings: ${gradleSettings}")

                        // Maven
                        configFileProvider([configFile(fileId: pipelineParams.mavenSettingsFileName, targetLocation: 'settings.xml')]) { }

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.JAVA_GRADLE, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        logger.debug('******** Environment variables ********') {
                                if (unix) {
                                    sh 'env'
                                }
                                else {
                                    bat 'set'
                                }
                            }
                        logger.debug('***************************************/n/n')

                        logger.debug('************* Gradle Tasks *************') {
                                if (unix) {
                                    sh gradleScript + ' -q tasks'
                                }
                                else {
                                    bat gradleScript + ' -q tasks'
                                }
                            }
                        logger.debug('***************************************/n/n')

                        logger.debug('************* Gradle Projects *************') {
                                if (unix) {
                                    sh gradleScript + ' -q projects'
                                }
                                else {
                                    bat gradleScript + ' -q projects'
                                }
                            }
                        logger.debug('***************************************')

                        buildGradleFile = new BuildGradleFile(this, pipelineParams.useGradleWrapper.toBoolean())
                        projectVersion = buildGradleFile.getVersion()

                        GitLabUtils.postStatus(this, 'running')

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Increase Patch Version') {
                when { expression { return Boolean.valueOf(pipelineParams.increasePatchVersion) && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.increasePatchVersionStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        projectVersion = IncreasePatchVersion.perform(this, buildGradleFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage ('Resolve Dependencies & Build') {
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    dir(PROJECT_ROOT_FOLDER) {
                        script {
                            stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                            FAILED_STAGE = env.STAGE_NAME

                            String gradleBuildTasks = pipelineParams.gradleBuildTasks
                            String buildGradleFileName = pipelineParams.buildGradleFileName

                            buildInfo = Artifactory.newBuildInfo()

                            buildInfo.env.capture = true

                            artifactoryGradle = Artifactory.newGradleBuild()
                            artifactoryGradle.tool = 'Gradle-5.5'
                            artifactoryGradle.resolver server: artifactoryServer, repo: pipelineParams.releaseRepo
                            artifactoryGradle.usesPlugin = true
                            artifactoryGradle.useWrapper = pipelineParams.useGradleWrapper.toBoolean()
                            artifactoryGradle.run buildFile: "${buildGradleFileName}".toString() , tasks: "${gradleBuildTasks}".toString(), buildInfo: buildInfo

                            stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                        }
                    }
                }
            }
            stage('Run Unit Tests') {
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    dir(PROJECT_ROOT_FOLDER) {
                        script {
                            stagesExecutionTimeTracker.runUnitTestsStageStart()
                            FAILED_STAGE = env.STAGE_NAME

                            String gradleTestTasks = pipelineParams.gradleTestTasks
                            String buildGradleFileName = pipelineParams.buildGradleFileName

                            buildInfo = Artifactory.newBuildInfo()
                            buildInfo.env.capture = true

                            artifactoryGradle = Artifactory.newGradleBuild()
                            artifactoryGradle.tool = 'Gradle-5.5'
                            artifactoryGradle.resolver server: artifactoryServer, repo: pipelineParams.releaseRepo
                            artifactoryGradle.usesPlugin = true
                            artifactoryGradle.useWrapper = pipelineParams.useGradleWrapper.toBoolean()
                            artifactoryGradle.run buildFile: "${buildGradleFileName}".toString() , tasks: "${gradleTestTasks}".toString(), buildInfo: buildInfo

                            stagesExecutionTimeTracker.runUnitTestsStageEnd()
                        }
                    }
                }
            }
            stage('Gating') {
                 parallel {
                     stage('Code Review') {
                         when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                         environment {
                             PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                         }
                         steps {
                             dir(PROJECT_ROOT_FOLDER) {
                                 script {
                                    stagesExecutionTimeTracker.codeReviewStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    String buildGradleFileName = pipelineParams.buildGradleFileName

                                    // Delete an earlier plugin's report-task.txt. Ref: https://jira.sonarsource.com/browse/SONARJNKNS-281
                                    if (unix) {
                                        sh 'rm -rf .scannerwork'
                                    }
                                    else {
                                        bat 'rmdir /s /q .scannerwork || exit 0'
                                    }
                                    String gradleCallParams = '--no-rebuild sonar'
                                    if (logger.level <= Level.DEBUG) {
                                        gradleCallParams = '--stacktrace --debug ' + gradleCallParams
                                    }
                                    buildInfo = Artifactory.newBuildInfo()
                                    buildInfo.env.capture = true

                                    artifactoryGradle = Artifactory.newGradleBuild()
                                    artifactoryGradle.tool = 'Gradle-5.5'
                                    artifactoryGradle.resolver server: artifactoryServer, repo: pipelineParams.releaseRepo
                                    artifactoryGradle.usesPlugin = true
                                    artifactoryGradle.useWrapper = pipelineParams.useGradleWrapper.toBoolean()
                                    SonarQubeRunner.runScan(this, PipelineType.JAVA_GRADLE, { sonarParams ->
                                                artifactoryGradle.run tasks: "${gradleCallParams} ${sonarParams}".toString(),
                                                        buildInfo: buildInfo, buildFile: "${buildGradleFileName}".toString()
                                        },
                                        unix, MRCommitsList, projectVersion)
                                    SonarQubeRunner.checkScan(this, sonarQubeResult)

                                    stagesExecutionTimeTracker.codeReviewStageEnd()
                                }
                            }
                        }
                    }
                    stage ('Open-Source Governance (BlackDuck)') {
                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                        environment {
                            BLACKDUCK = credentials("${pipelineParams.hubUserPasswordTokenName}")
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.JAVA_GRADLE)
                                blackDuckResult = blackDuckRunner.callBlackDuck('')

                                if (!blackDuckResult.governanceGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.hubFailPipelineOnFailedOpenSourceGovernance)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Open-Source Governance assessment: ${blackDuckResult.message}")
                                    }
                                    else if (currentBuild.result != 'FAILED') {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }

                                stagesExecutionTimeTracker.openSourceGovernanceStageEnd()
                            }
                        }
                    }
                    stage ('Open-Source Governance (Snyk)') {
                        when { expression { return SnykRunner.isRequested(this, params.forceFullScan, localBranchName) } }
                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                snykRunner.call('')

                                if (!snykRunner.result.governanceGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.snykGatingEnabled)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Open-Source Governance assessment: ${snykRunner.result.message}")
                                    }
                                    else if (currentBuild.result != 'FAILED') {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }

                                stagesExecutionTimeTracker.openSourceGovernanceStageEnd()
                            }
                        }
                    }
                    stage ('Security Code Scanning') {
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                logger.warning('[WARNING] Fortify integration is not supported yet.')
                            }
                        }
            //            when { expression { return FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName) } }
            //                environment {
            //                    FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
            //            }
            //            steps {
            //                script {
            //                    def fortifyBuildId = "GRADLE-${JOB_BASE_NAME}"
            //                    fortifyRunner = new FortifyRunner(scriptObj: this,
            //                                                      localBranchName: localBranchName,
            //                                                      pipelineParams: pipelineParams,
            //                                                      buildId: fortifyBuildId)
            //                    fortifyRunner.init()
            //                    fortifyResult = fortifyRunner.run()

            //                    if (!fortifyResult.codeSecurityGatePassed) {
            //                        if (Boolean.valueOf(pipelineParams.fortifyGating)) {
            //                            currentBuild.result = 'FAILED'
            //                            error("Failed on Code Security assessment: ${fortifyResult.message}")
            //                        }
            //                        else if (currentBuild.result != 'FAILED') {
            //                            currentBuild.result = 'UNSTABLE'
            //                        }
            //                    }
            //                }
            //            }
            //         }
                    }
                }
            }
            stage('Package and Store') {
                when {
                    expression {
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) &&
                               pipelineParams.artifactoryDeploymentPattern != null &&
                               ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)
                    }
                }
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    dir(PROJECT_ROOT_FOLDER) {
                        script {
                            stagesExecutionTimeTracker.packageAndStoreStageStart()
                            FAILED_STAGE = env.STAGE_NAME

                            ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)

                            // Check that there isn't already an artifact with that commit id in Artifactory
                            def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                                  pipelineParams.artifactoryDeploymentPattern,
                                                                                  pipelineParams.releaseRepo,
                                                                                  pipelineParams.snapshotRepo)

                            if (artifactExists) {
                                logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                                logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            }
                            else {
                                artifactoryHelper.uploadGradleArtifact(pipelineParams,
                                                                       GIT_COMMIT,
                                                                       gradleSettings,
                                                                       sonarQubeResult.codeQualityGatePassed,
                                                                       blackDuckResult.governanceGatePassed,
                                                                       snykRunner.result.governanceGatePassed,
                                                                       fortifyResult.codeSecurityGatePassed,
                                                                       sonarQubeResult.message,
                                                                       blackDuckResult.message,
                                                                       snykRunner.result.message,
                                                                       fortifyResult.message,
                                                                       projectVersion.toString())
                            }

                            stagesExecutionTimeTracker.packageAndStoreStageEnd()
                        }
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
                steps {
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                          wait: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                }
            }
        }
        post {
            always {
                archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true

                script {
                    // Only the latest matching report kind may have failures
                    // because jobs normally fail on test failures.  Therefore,
                    // check for the integration test report first and ignore
                    // the unit test report as the latter would have 100%
                    // success.
                    def testReportSearch = findFiles glob: "${pipelineParams.integrationTestReport}"
                    if (testReportSearch.length) {
                        junit allowEmptyResults: true, testResults: "${pipelineParams.integrationTestReport}"
                    }
                    junit allowEmptyResults: true, testResults: "${pipelineParams.unitTestReport}"

                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()

                }
                script {
                    if (isUnix()) {
                        cleanWs()
                    }
                }
            }
        }
        parameters {
            booleanParam(
                name: 'forceFullScan',
                defaultValue: false,
                description: 'Used to force the calls to BlackDuck and Fortify for development branch'
            )
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
        triggers {
            gitlab(
                triggerOnPush: configuration.jenkinsJobTriggerOnPush,
                triggerOnMergeRequest: configuration.jenkinsJobTriggerOnMergeRequest,
                triggerOpenMergeRequestOnPush: 'never',
                triggerOnNoteRequest: true,
                noteRegex: '.*[Jj]enkins.*[Bb]uild.*',
                skipWorkInProgressMergeRequest: true,
                ciSkip: true,
                setBuildDescription: true,
                addNoteOnMergeRequest: true,
                addCiMessage: true,
                addVoteOnMergeRequest: true,
                acceptMergeRequestOnSuccess: false,
                branchFilterType: 'RegexBasedFilter',
                targetBranchRegex: configuration.jenkinsJobRegEx,
                secretToken: configuration.jenkinsJobSecretToken)
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName',
                                          'Defaulting deploymentJenkinsJobName to null.  ' +
                                            'Could be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this pipelines.',
                                          null)
    propertiesCatalog.addOptionalProperty('integrationTestReport', 'Location of the integration test report', 'target/failsafe-reports/*.xml')
    propertiesCatalog.addOptionalProperty('unitTestReport', 'Location of unit test report', 'build/test-results/test/*.xml')
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')
    propertiesCatalog.addOptionalProperty('projectRootFolder', 'Defaulting projectRootFolder to the root Folder', '.')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    GradlePropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)

    return propertiesCatalog
}
