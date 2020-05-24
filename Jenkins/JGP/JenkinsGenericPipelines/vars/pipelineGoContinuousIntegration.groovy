import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.blackduck.BlackDuckResult
import com.manulife.fortify.FortifyResult
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
import com.manulife.sonarqube.SonarQubeResult
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
        environment {
            SONARQUBE_RUNNER = '//Users//dsmobile-imac//sonar-runner//bin//sonar-runner'
            PATH  = "/usr/local/bin:$PATH"
            GOPATH = "${WORKSPACE}"
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

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.GO, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        // Fortify
                        fortifyResult = new FortifyResult()
                        stagesExecutionTimeTracker.initStageEnd()
                    }

                    script {
                        logger.debug('Fixing folders structure so that we have a src/<gitlabprojectname>/... folder in the workspace as expected by GoLang')
                        sh "mkdir -p ${pipelineParams.projectFinalRootFolder}"
                        sh "mv -v ${pipelineParams.projectRootFolder}/* ${pipelineParams.projectFinalRootFolder}"
                    }

                    script {
                        logger.debug('DEBUG INFORMATION:')
                        logger.debug('Go Version:') { sh 'go version' }
                        logger.debug('dep Version:') { sh 'dep version' }
                        logger.debug('Current directory:') { sh 'pwd' }
                        logger.debug('Folder Content:') { sh 'ls -Ral' }
                    }
                }
            }
            stage ('Resolve Dependencies') {
                when { expression { return pipelineParams.dependencyCommand } }
                steps {
                    dir("${pipelineParams.projectFinalRootFolder}") {
                        sh "${pipelineParams.dependencyCommand}"
                    }
                }
            }
            stage ('Build') {
                when { expression { return pipelineParams.buildCommand } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def commandStr = "GOOS=${pipelineParams.goos} GOARCH=${pipelineParams.goarch} ${pipelineParams.buildCommand}"
                        logger.debug("Build Command: ${commandStr}")
                        dir("${pipelineParams.projectFinalRootFolder}") {
                            //sh "GOOS=${pipelineParams.goos} GOARCH=${pipelineParams.goarch} ${pipelineParams.buildCommand}"
                            sh "${commandStr}"
                        }

                        logger.debug('DEBUG INFORMATION:') { sh 'ls -Ral bin' }
                    }
                }
            }
            stage('Run Tests') {
                when { expression { return pipelineParams.testCommand } }
                steps {
                    dir("${pipelineParams.projectFinalRootFolder}") {
                        sh "${pipelineParams.testCommand}"
                    }
                }
            }
            stage('Gating') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.warning('Not implemented yet')
                    }
                }
            }
            stage('Package and Store') {
                when { expression { return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && pipelineParams.binaryReleaseRepo && 'MERGE' != env.gitlabActionType } }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                               "**/${pipelineParams.binaryFileName}",
                                                                               pipelineParams.binaryReleaseRepo)
                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            return
                        }

                        def subFolder = "${pipelineParams.goos}_${pipelineParams.goarch}"
                        logger.debug("subFolder = ${subFolder}")
                        artifactoryHelper.uploadArtifact(pipelineParams,
                                                         "bin/${subFolder}/${pipelineParams.binaryFileName}",
                                                         "${pipelineParams.binaryReleaseRepo}/${pipelineParams.binaryFileName}/${BUILD_NUMBER}/${pipelineParams.binaryFileName}",
                                                         sonarQubeResult.codeQualityGatePassed,
                                                         blackDuckResult.governanceGatePassed,
                                                         snykRunner.result.governanceGatePassed,
                                                         fortifyResult.codeSecurityGatePassed,
                                                         sonarQubeResult.message,
                                                         blackDuckResult.message,
                                                         snykRunner.result.message,
                                                         fortifyResult.message,
                                                         BUILD_NUMBER)
                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName && ('MERGE' != env.gitlabActionType) } }
                steps {
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                          wait: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()

                }
                script {
                    if (params.debug_mode != true) {
                        cleanWs()
                    }
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
                name: 'debug_mode',
                defaultValue: false,
                description: 'Allows execution of the pipeline in debug mode which will output more information to help debugging a project configuration.'
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
    propertiesCatalog.addMandatoryProperty('projectRootFolder',
                                           'Missing projectRootFolder mandatory property.  Must be set to the project root folder (as in GitLab)')
    propertiesCatalog.addMandatoryProperty('projectFinalRootFolder',
                                           'Missing projectFinalRootFolder mandatory property.  Must be set to the GoLang project root folder as it should be on disk in Jenkins.')

    propertiesCatalog.addOptionalProperty('dependencyCommand',
                                          'Defaulting dependencyCommand property to \"dep ensure\"',
                                          'dep ensure')
    propertiesCatalog.addOptionalProperty('goos',
                                          'Defaulting goos property to \"linux\".  ' +
                                            'See https://www.digitalocean.com/community/tutorials/how-to-build-go-executables-for-multiple-platforms-on-ubuntu-16-04 for supported values.',
                                          'linux')
    propertiesCatalog.addOptionalProperty('goarch',
                                          'Defaulting goarch property to \"amd64\".  ' +
                                            'See https://www.digitalocean.com/community/tutorials/how-to-build-go-executables-for-multiple-platforms-on-ubuntu-16-04 for supported values.',
                                          'amd64')
    propertiesCatalog.addOptionalProperty('buildCommand', 'Defaulting buildCommand property to \"go install\"', 'go install')
    propertiesCatalog.addOptionalProperty('testCommand', 'Defaulting testCommand property to null.', null)
    propertiesCatalog.addOptionalProperty('binaryFileName',
                                          'Defaulting binaryFileName to null.  Can be set to the name of the name of the exe created by the build.',
                                          null)
    propertiesCatalog.addOptionalProperty('binaryReleaseRepo',
                                          'Defaulting binaryReleaseRepo to null.  Can be set to the name of the generic Artifactory repository where the binaries should be uploaded.',
                                          null)
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.GO)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.GO)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.GO)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.GO)

    return propertiesCatalog
}
