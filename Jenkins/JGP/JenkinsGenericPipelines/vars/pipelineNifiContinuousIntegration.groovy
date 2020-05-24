import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
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
import com.manulife.sonarqube.SonarQubePropertiesCalalogBuilder
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader

// TODO:
//  - Upload to Artifactory using the Jenkins plugin so that we have the commit_id on the artifact

def call(Map configuration) {

    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        // The environment and tools sections will be replaced by the Docker container configuration when we move to the new Jenkins servers.
        environment {
            SONARQUBE_RUNNER = "\"${tool 'SonarQube Runner'}\\bin\\sonar-runner\""
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

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Download Classification Data') {
                steps
                {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        dir('ClassificationData') {
                            try {
                                checkout([
                                    $class: 'GitSCM', branches: [[name: "$pipelineParams.NIFI_branch"]],
                                    userRemoteConfigs: [[url: "$pipelineParams.Classification_Data_REPO",
                                                         credentialsId:"$pipelineParams.gitLabSSHCredentialsId"]]
                                ])
                            }
                            catch (err) {
                                logger.error("Error downloading classification data: ${err}", err)
                                errMsg = 'Failure'
                                error('Git clone failed on Jenkins, please use manual-deploy instead')
                            }
                        }
                    }
                }
            }
            stage('Package and Store') {
                when {
                    expression {
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && pipelineParams.nodePackageCommand != null && 'MERGE' != env.gitlabActionType
                    }
                }
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        def extension = '*.tar.gz'

                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                              extension,
                                                                              pipelineParams.releaseRepo)
                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            return
                        }

                        sh 'rm -rf artifact.*.tar.gz ClassificationData/'
                        // Create an tar archive (It would be better in zip because PCF can only handle
                        // zip or folder (not tarball) But we don't have `zip` binary installed on Jenkins for now)
                        sh "tar -czf ${gitlabSourceRepoName}.tar.gz ClassificationData/"

                    // TODO: That method doesn't exist...
                        artifactoryHelper.uploadArtifact(pipelineParams,
                            "ClassificationData/${gitlabSourceRepoName}.tar.gz"
                            )

                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName } }
                steps {
                    script {
                        build job: "${pipelineParams.deploymentJenkinsJobName}",
                            wait: false,
                            parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                    }
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, null, null, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()
                }
                cleanWs()
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
                description: 'Used to force the calls to Sonar for development branch'
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
                noteRegex: 'Jenkins please retry a build',
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

    propertiesCatalog.addOptionalProperty('projectType', 'Defaulting projectType to Nifi.', 'Nifi')
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)

    propertiesCatalog.addOptionalProperty('increaseVersion', 'Defaulting increaseVersion to null.  Setting it to major, minor, patch increments the respective part of the version.', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NIFI)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.NIFI)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NIFI)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NIFI)

    return propertiesCatalog
}