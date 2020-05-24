import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.git.GitPropertiesCatalogBuilder
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
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Package and Store') {
                when {
                    expression {
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) && 'MERGE' != env.gitlabActionType
                    }
                }
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Content of src folder:') { sh 'ls src' }
                        logger.debug('Content of current folder:') { sh 'ls -al' }

                        // Create target directory where to copy changed files
                        sh "mkdir -p ${pipelineParams.DataDirectoryName}"

                        // Find files which are changed for the commit id to copy them to target directory
                        sh "git diff-tree --pretty='' --name-only -r ${GIT_COMMIT}"
                        def targetDirectoryEmpty = true
                        try {
                            def filesInCommitOneString = sh (returnStdout: true,
                                                             script: "git diff-tree --pretty='' --name-only -r ${GIT_COMMIT}")

                            logger.debug("Files in the commits are: ${filesInCommitOneString}")

                            filesInCommitOneString.split('\\r?\\n').each { fileInCommit ->
                                logger.debug("File ${fileInCommit} in Commit to match pattern ${pipelineParams.SourceDirectoryName}/ and ${pipelineParams.FilePattern}")
                                logger.debug("${fileInCommit.contains("${pipelineParams.SourceDirectoryName}/")}")
                                logger.debug("${fileInCommit.contains("${pipelineParams.FilePattern}")}")
                                logger.debug("${fileInCommit}")

                                if (fileInCommit.contains("${pipelineParams.SourceDirectoryName}/")
                                    && fileInCommit.contains("${pipelineParams.FilePattern}")) {
                                    logger.debug('file name to match for extension pattern and directory name')
                                    logger.debug("${fileInCommit}")
                                    logger.debug("${(fileExists(file: "${fileInCommit}"))}")
                                    logger.debug("${fileInCommit}")

                                    if (fileExists(file: "${fileInCommit}")) {
                                        try {
                                            logger.debug("${fileInCommit}")
                                            sh "cp -R -f ${fileInCommit} ${pipelineParams.DataDirectoryName}/"
                                            targetDirectoryEmpty = false
                                            logger.info('file copied to target directory')
                                        }
                                        catch (err) {
                                            logger.error("Unexpected Exception: ${err}", err)
                                        }
                                    }
                                    else {
                                        logger.warning("file ${fileInCommit} does not exist in source directory ${pipelineParams.SourceDirectoryName}")
                                    }
                                }
                            }
                            logger.debug("Content of ${pipelineParams.DataDirectoryName} folder:") {
                                    sh "ls ${pipelineParams.DataDirectoryName}/"
                                }
                            // Check if any data file exists
                            if (targetDirectoryEmpty) {
                                logger.warning('Target directory is empty, no file to pass to loader')
                                return
                            }
                        }
                        catch (err) {
                            logger.error("Unexpected Exception: ${err}", err)
                            errMsg = 'Failure'
                            error('Git clone failed on Jenkins, please use manual-deploy instead')
                        }

                        def extension = '*.tar.gz'
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                               extension,
                                                                               pipelineParams.releaseRepo)
                        if (artifactExists) {
                            logger.warning("Source Repo Name ${gitlabSourceRepoName}")
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            return
                        }

                        // Create an tar archive (It would be better in zip because PCF can only handle
                        // zip or folder (not tarball) But we don't have `zip` binary installed on Jenkins for now)
                        sh "ls ${pipelineParams.DataDirectoryName}/"
                        sh "tar -czf ${gitlabSourceRepoName}.tar.gz ${pipelineParams.DataDirectoryName}/"

                        // Reads version number for tar file
                        String versionFileContents = readFile("${pipelineParams.SourceDirectoryName}/${pipelineParams.VersionFile}")
                        def versionNumbers = versionFileContents.split('\n')

                        logger.debug("Version File Contents: ${versionFileContents}")

                        def versionNo = versionNumbers[0]
                        logger.debug("Version Number: ${versionNo}")

                        def outputFileName = "${gitlabSourceRepoName}.${versionNo}.tar.gz"
                        if (localBranchName != 'dev') {
                            $outputFileName = "${gitlabSourceRepoName}.RELEASE-${versionNo}.tar.gz"
                        }

                        logger.debug("Version Number: ${versionNo}")
                        logger.debug("Output File Name : ${outputFileName}")

                        artifactoryHelper.uploadArtifact(pipelineParams,
                            "${gitlabSourceRepoName}.tar.gz",
                            "${pipelineParams.releaseRepo}/${gitlabSourceRepoName}/${outputFileName}",
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
                    logger.debug("Source Repo Name in Trigger Deployment ${gitlabSourceRepoName}")
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                        wait: false,
                        parameters:
                            [
                                [
                                    $class: 'StringParameterValue',
                                    name: 'commit_id',
                                    value: "${GIT_COMMIT}"
                                ],
                                [
                                    $class: 'StringParameterValue',
                                    name: 'gitlabSourceRepoName',
                                    value: "${gitlabSourceRepoName}"
                                ]
                            ]
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
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
    propertiesCatalog.addOptionalProperty('projectType', 'Defaulting projectType to NodeJS.  Could also be set to ReactJS', 'ShellExec')
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)

    propertiesCatalog.addOptionalProperty('increaseVersion', 'Defaulting increaseVersion to null.  Setting it to major, minor, patch increments the respective part of the version.', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')

    propertiesCatalog.addOptionalProperty('SourceDirectoryName', 'Defaulting Artifacts upload path to Input.', 'src')
    propertiesCatalog.addOptionalProperty('DataDirectoryName', 'Defaulting Artifacts upload path to DataForShell.', 'DataForShell')
    propertiesCatalog.addOptionalProperty('FilePattern', 'Defaulting Artifacts upload path to .xlsx.', '.xlsx')
    propertiesCatalog.addOptionalProperty('VersionFile', 'Defaulting Artifacts upload path to 0.0.1.', 'version.txt')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}