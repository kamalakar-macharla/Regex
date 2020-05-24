import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.logger.Logger
import com.manulife.git.GitPropertiesCatalogBuilder
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
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
//  - Upload to Artifactory using the Jenkins plugin so that we have the commit_id on the artifact

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
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
                        increaseVersion = pipelineParams.increaseVersion ?: (Boolean.valueOf(pipelineParams.increasePatchVersion) ? 'patch' : null)
                        GitLabUtils.postStatus(this, 'running')
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Execute the python script to replace variable') {
                steps {
                    script {
                        stagesExecutionTimeTracker.executePythonScriptStart()
                        FAILED_STAGE = env.STAGE_NAME
                        try {
                            //Check if python script has access to be run?
                            sh """

                                ##### run python script to replace the variable for test uat and prod, pass credentials in the shell script
                                python ${pipelineParams.pythonScriptName} ${pipelineParams.environmentToDeploy}

                                """
                        }
                        catch (e) {
                            error("Unable to execute python script due to the unexpected error(s), error: ${e.message}")
                        }
                        stagesExecutionTimeTracker.executePythonScriptEnd()
                    }
                }
            }
            stage('Package and Store') {
                when {
                    expression {
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && 'MERGE' != env.gitlabActionType
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)

                        logger.debug('Data Directory Content') { "ls ${pipelineParams.DataDirectoryName}" }
                        sh "tar -czf ${gitlabSourceRepoName}.tar.gz ${pipelineParams.DataDirectoryName}/"

                        // Reads version number for tar file
                        String versionFileContents = readFile("${pipelineParams.SourceDirectoryName}/${pipelineParams.VersionFile}")
                        def versionNumbers = versionFileContents.split('\n')

                        logger.debug("Version File Contents: ${versionFileContents}")

                        def versionNo = versionNumbers[0]
                        logger.debug("Version Number: ${versionNo}")

                        def outputFileName = "${gitlabSourceRepoName}-${pipelineParams.environmentToDeploy}-${versionNo}.tar.gz"
                        if (localBranchName != 'dev') {
                            $outputFileName = "${gitlabSourceRepoName}.RELEASE-${pipelineParams.environmentToDeploy}-${versionNo}.tar.gz"
                        }
                        logger.debug("Version Number: ${versionNo}")
                        logger.debug("Output File Name : ${outputFileName}")

                        artifactoryHelper.uploadArtifact(pipelineParams,
                            "${gitlabSourceRepoName}.tar.gz",
                            "${pipelineParams.releaseRepo}/${gitlabSourceRepoName}/${outputFileName}",
                            //"${pipelineParams.releaseRepo}/${outputFileName}",
                            '',
                            '',
                            '',
                            '',
                            '',
                            '',
                            '',
                            '',
                            "${versionNo}")
                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when {
                    expression {
                        return pipelineParams.deploymentJenkinsJobName
                    }
                }
                steps {
                    echo "Source Repo Name in Trigger Deployment ${gitlabSourceRepoName}"
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                        wait: false,
                        parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"],
                                     [$class: 'StringParameterValue', name: 'gitlabSourceRepoName', value: "${gitlabSourceRepoName}"]
                        ]
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this, '')
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                    new SharedLibraryReport(this).print()
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
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
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
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)

    propertiesCatalog.addOptionalProperty('SourceDirectoryName', 'Defaulting Artifacts upload path to Input.', null)
    propertiesCatalog.addOptionalProperty('DataDirectoryName', 'Defaulting Artifacts upload path to DataForPython.', null)

    propertiesCatalog.addOptionalProperty('VersionFile', 'Defaulting Artifacts upload path to 0.0.1.', null)
    propertiesCatalog.addOptionalProperty('pythonScriptName', 'Defaulting python scripting for repacing the value frm DEV to TST', null)
    propertiesCatalog.addOptionalProperty('environmentToDeploy', 'Defaulting environment variable where we want to deploy', null)
    propertiesCatalog.addOptionalProperty('targetLocation', 'Defaulting location where we want to copy our RSLS files', null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
