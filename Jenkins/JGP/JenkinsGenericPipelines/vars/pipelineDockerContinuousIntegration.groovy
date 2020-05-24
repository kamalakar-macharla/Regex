import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.blackduck.BlackduckPropertiesCalalogBuilder
import com.manulife.blackduck.BlackDuckRunner
import com.manulife.blackduck.BlackDuckResult
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
import com.manulife.sonarqube.EnvironmentVariablesInitializer
import com.manulife.sonarqube.SonarQubePropertiesCalalogBuilder
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.sonarqube.SonarQubeRunner
import com.manulife.sonarqube.SonarQubeUtils
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.Shell

// TODO:
//  - Upload to Artifactory using the Jenkins plugin so that we have the commit_id on the artifact

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
                        snykRunner = new SnykRunner(this, PipelineType.DOCKER, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        increaseVersion = pipelineParams.increaseVersion ?: (Boolean.valueOf(pipelineParams.increasePatchVersion) ? 'patch' : null)

                        GitLabUtils.postStatus(this, 'running')

                        // No notion of project version for Docker
                        projectVersion = null

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage ('Resolve Dependencies & Build') {
                steps {
                    script {
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        sh """
                            docker --version
                            docker build -t ${pipelineParams.dockerImageName }:$BUILD_NUMBER ${ pipelineParams.dockerFileLocation }
                        """
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Run Unit Tests') {
                when { expression { return pipelineParams.dockerUnitTestCommand } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('Skipped Unit Tests. No unit test for docker currently remove \'None Blocking\'')
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        environment {
                            SONAR_TOKEN = credentials("${EnvironmentVariablesInitializer.getSonarQubeTokenName(env.SONAR_ENVIRONMENT)}")
                            GITLAB_API_TOKEN = credentials("${pipelineParams.gitLabAPITokenName}")
                            SONARQUBE_RUNNER = "\"${tool 'SonarQube Runner'}\\bin\\sonar-runner\""
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                stagesExecutionTimeTracker.codeReviewStageStart()
                                SonarQubeRunner.runnerScan(this, PipelineType.DOCKER, unix, MRCommitsList, sonarQubeResult, projectVersion)
                                stagesExecutionTimeTracker.codeReviewStageEnd()
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

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.DOCKER)
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

                                snykRunner.call("--docker ${pipelineParams.dockerImageName}:${BUILD_NUMBER}")

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
                    stage ('Security Code Scanning') {
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                logger.info('Skipped Security Code scanning because it\'s currently not supported by Fortify.')
                            }
                        }
                    }
                }
            }
            stage('Package and Store') {
                when { expression { return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && 'MERGE' != env.gitlabActionType } }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        try {
                            withCredentials([usernamePassword(credentialsId: "${pipelineParams.dockerLoginCredential}", usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PSW')]) {
                                sh "docker login -u $DOCKER_USERNAME -p $DOCKER_PSW ${pipelineParams.dockerArtifactoryRepo}.${pipelineParams.dockerArtifactoryURL}"
                            }
                        }
                        catch (e) {
                            logger.error("Unable to login into Docker (${pipelineParams.dockerArtifactoryRepo}.${pipelineParams.dockerArtifactoryURL}) " +
                                           "from ${pipelineParams.dockerLoginCredential}, error: ${e.message}", e)
                        }

                        //  # automatic done on agent
                        sh """
                            docker tag ${pipelineParams.dockerImageName}:$BUILD_NUMBER ${pipelineParams.dockerArtifactoryRepo}.${pipelineParams.dockerArtifactoryURL}/${pipelineParams.dockerImageName}:${pipelineParams.dockerTag}
                            docker push ${pipelineParams.dockerArtifactoryRepo}.${pipelineParams.dockerArtifactoryURL}/${pipelineParams.dockerImageName}:${pipelineParams.dockerTag}
                        """
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
                archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true

                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, null, sonarQubeResult).printText()
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
                description: 'Used to force the calls to BlackDuck and Fortify for development branch'
            )
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '1'))
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

    propertiesCatalog.addMandatoryProperty('dockerImageName', '[Error] Missing dockerImageName property value which must be set to the image name building..')

    propertiesCatalog.addOptionalProperty('dockerFileLocation', 'Defaulting dockerFileLocation property to docker file location. Default look for dockerfile in to root folder.', '.')
    propertiesCatalog.addOptionalProperty('dockerTag', 'Defaulting dockerTag property to latest.', 'latest')
    propertiesCatalog.addOptionalProperty('dockerArtifactoryURL', 'Defaulting dockerArtifactoryURL property to global Artifactory server.', 'artifactory.platform.manulife.io')
    propertiesCatalog.addOptionalProperty('dockerArtifactoryRepo', 'Defaulting dockerArtifactoryRepo property to repo on Artifactory server.', 'docker-local')
    propertiesCatalog.addOptionalProperty('dockerLoginCredential', 'Defaulting dockerLoginCredential property to repo on docker login credential.', 'docker-login')
    propertiesCatalog.addOptionalProperty('releaseRepo', 'Defaulting releaseRepo property to null. Should be set to the Artifactory release repo name. An example would be example-npm', null)
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)

    return propertiesCatalog
}
