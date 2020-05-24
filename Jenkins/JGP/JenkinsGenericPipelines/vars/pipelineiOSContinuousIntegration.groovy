import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
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


def call(Map configuration) {

    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        // The environment and tools sections will be replaced by the Docker container configuration when we move to the new Jenkins servers.
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

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                         // checking if Fastfile is there
                        def exists = fileExists 'fastlane/Fastfile'
                        if (!exists) {
                            error('A fastfile is needed in order to pursue with the execution of this pipeline')
                        }

                        //defining local branch name needed for later scans
                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        unix = isUnix()

                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)

                        //Storing properties in a map
                        pipelineParams = new Properties()

                        // Testing the validity of the properties file
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        logger.debug("Artifactory URL: ${artifactoryServer.url}")

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.SWIFT, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()

                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        //TODO Need to cleanup the use of increasePatchVersion and increaseVersion but this would be a breaking change for end users
                        increaseVersion = pipelineParams.increaseVersion ?: (Boolean.valueOf(pipelineParams.increasePatchVersion) ? 'patch' : null)
                        increaseVersion = Boolean.valueOf(pipelineParams.increaseBetaRelease) ? 'prerelease --preid=beta' : increaseVersion

                         //GitLabNotification
                        GitLabUtils.postStatus(this, 'running')

                        // TODO: Figure out how to deal with projectVersion
                        projectVersion = null

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        environment {
                            GITLAB_API_TOKEN = credentials("${pipelineParams.gitLabAPITokenName}")
                            SONARQUBE_RUNNER = "\"${tool 'SonarQube Runner'}\\bin\\sonar-runner\""
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.codeReviewStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                SonarQubeRunner.runnerScan(this, PipelineType.SWIFT, unix, MRCommitsList, sonarQubeResult, projectVersion)
                                stagesExecutionTimeTracker.codeReviewStageEnd()
                            }
                        }
                    }
                    stage('Open-Source Governance (BlackDuck)') {

                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                            environment {
                                BLACKDUCK = credentials("${pipelineParams.hubUserPasswordTokenName}")
                        }

                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.SWIFT)
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

                                if (!snykRunner.getResult().governanceGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.snykGatingEnabled)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Open-Source Governance assessment: ${snykRunner.getResult().message}")
                                    }
                                    else if (currentBuild.result != 'FAILED') {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }

                                stagesExecutionTimeTracker.openSourceGovernanceStageEnd()
                            }
                        }
                    }
                    stage('Security Code Scanning') {
                        when { expression { return FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName) } }
                            environment {
                                FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
                                GITLAB_API_TOKEN = credentials("${pipelineParams.gitLabAPITokenName}")
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                def fortifyBuildId = "SWIFT-${JOB_BASE_NAME}"
                                fortifyRunner = new FortifyRunner(scriptObj: this,
                                                                  localBranchName: localBranchName,
                                                                  pipelineParams: pipelineParams,
                                                                  buildId: fortifyBuildId)
                                fortifyRunner.init()
                                fortifyResult = fortifyRunner.run()

                                if (!fortifyResult.codeSecurityGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.fortifyGating)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Code Security assessment: ${fortifyResult.message}")
                                    }
                                    else if (currentBuild.result != 'FAILED') {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage('Fastlane Unit Tests') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        echo "shell: ${SHELL}, user: ${USER}"
                        sh """
                        source ~/.bash_profile

                        export LC_ALL=en_US.UTF-8
                        export LANG=en_US.UTF-8
                        export FASTLANE_DISABLE_COLORS=1

                        rbenv init -
                        bundle install
                        ${pipelineParams.fastlaneUnitTestsCmd}
                        """
                    }
                }
            }
            stage('Fastlane Build') {
                steps {
                    script {
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        sh """
                        source ~/.bash_profile

                        export LC_ALL=en_US.UTF-8
                        export LANG=en_US.UTF-8
                        export FASTLANE_DISABLE_COLORS=1

                        rbenv init -
                        bundle install
                        ${pipelineParams.fastlaneBuildCmd}

                        """
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Package and Store') {
                when { expression { return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        def extension = '*.ipa'

                        // Check if already exist
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)

                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                               extension,
                                                                              pipelineParams.releaseRepo)
                        if (artifactExists) {
                            echo "[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}."
                            echo '[WARNING] Will skip the upload to Artifactory.'
                            return
                        }

                        if (fileExists("${pipelineParams.projectName}.ipa")) {

                            artifactoryHelper.uploadArtifact(pipelineParams,
                                                            "${pipelineParams.projectName}.ipa",
                                                            "${pipelineParams.releaseRepo}/${projectName}.ipa",
                                                            sonarQubeResult.codeQualityGatePassed,
                                                            blackDuckResult.governanceGatePassed,
                                                            snykRunner.result.governanceGatePassed,
                                                            fortifyResult.codeSecurityGatePassed,
                                                            sonarQubeResult.message,
                                                            blackDuckResult.message,
                                                            snykRunner.result.message,
                                                            fortifyResult.message,
                                                            BUILD_NUMBER)
                        }
                        else {
                            echo 'Skipped over upload to Artifactory because .ipa artifact did not exist'
                        }

                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
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
                cleanWs()
                script {
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()
                }
            }
        }
        parameters {
            choice(
              name: 'loggingLevel',
              choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
              description: 'Logging level to use in the job console'
            )
            booleanParam(
                name: 'forceFullScan',
                defaultValue: false,
                description: 'Used to force the calls to BlackDuck and Fortify for development branch'

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

    propertiesCatalog.addMandatoryProperty('projectName', 'Name of project')
    propertiesCatalog.addMandatoryProperty('fastlaneBuildCmd', 'Fastlane cmd to use fastfile for building and signing')
    propertiesCatalog.addMandatoryProperty('fastlaneUnitTestsCmd', 'Fastlane cmd to use fastfile for running unit tests')
    propertiesCatalog.addOptionalProperty('releaseRepo', 'Defaulting releaseRepo property to null. Should be set to the Artifactory release repo name. An example would be example-npm', null)
    propertiesCatalog.addOptionalProperty('deployementJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)
    propertiesCatalog.addOptionalProperty('unitTestSecret', 'Defaulting unitTestSecret property to null.', null)
    propertiesCatalog.addOptionalProperty('increaseVersion', 'Defaulting increaseVersion to null.  Setting it to major, minor, patch increments the respective part of the version.', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')
    propertiesCatalog.addOptionalProperty('increaseBetaRelease', 'Defaulting increaseBetaRelease to null.  Setting it to true will create a beta version tag. ' +
                                                                   'If increaseVersion was also used it will be overwritten by this value', null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    return propertiesCatalog
}
