import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
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

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.agent}"
        }
        stages {
            stage('Read Parameters') {
                steps {
                     script {
                        FAILED_STAGE = env.STAGE_NAME
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)


                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)

                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                    }
                }
            }
            stage('Execute the shell script') {
                steps {
                    script {
                        try {
                            FAILED_STAGE = env.STAGE_NAME
                            def extension = '*.tar.gz'
                            logger.debug('Initializing artifactoryserver')
                            ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                            logger.debug('downloading artifacts')
                            def downloadLocation = artifactoryHelper.downloadArtifact(GIT_COMMIT,
                                                            extension,
                                                            pipelineParams.releaseRepo)
                            logger.debug("downloaded artifacts at ${downloadLocation}")
                            logger.debug('----  Content of current Directory  ----') { sh 'ls' }
                            logger.debug('----  Content of data Directory  ----') { sh "ls ${downloadLocation}" }
                            sh "tar -xzvf ${downloadLocation}${gitlabSourceRepoName}.*.tar.gz"
                            logger.debug('Unzip completed')
                            logger.debug('----  Content of current Directory  ----') { sh 'ls' }

                            withCredentials([[$class: 'UsernamePasswordMultiBinding',
                                                credentialsId: "${pipelineParams.shellscriptcredentialid}",
                                                usernameVariable: 'USERNAME',
                                                passwordVariable: 'PASSWORD']]) {
                                //Check if shell script has access to be run?
                                bat """
                                    ----  Content of source directory  ----
                                    ls ${pipelineParams.SourceDirectoryName}/
                                    ----  Content of current directory  ----
                                    ls
                                    ----  Content of data directory  ----
                                    ls ${pipelineParams.DataDirectoryName}/
                                    ##### run shell script to move  file to Loader location, pass credentials in the shell script
                                    ${pipelineParams.SourceDirectoryName}/${pipelineParams.ShellScriptName} ${pipelineParams.DataDirectoryName}/ ${pipelineParams.shellhosturl} ${pipelineParams.shellhostport} ${env.USERNAME} ${env.PASSWORD}
                                    """
                            }
                        }
                        catch (err) {
                            logger.error("Error:${err}", err)
                            errMsg = 'Failure'
                        }
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
                    new ProductionSupportInfo(this).print()
                }
                script {
                    logger.debug('clearing work space')
                    cleanWs()
                    logger.debug('cleared work space')
                }
            }
        }
        parameters {
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
            string(
                name: 'commit_id',
                defaultValue: 'latest',
                description: 'Git Commit hash to deploy.'
            )
            string(
                name: 'gitlabSourceRepoName',
                defaultValue: 'latest',
                description: 'Git Lab Source repository name'
            )
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addOptionalProperty('ShellScriptName', 'Defaulting ShellScriptName property to loadershell', 'config-uploader.bat')
    propertiesCatalog.addOptionalProperty('SourceDirectoryName', 'Defaulting Artifacts upload path to Input.', 'src')
    propertiesCatalog.addOptionalProperty('DataDirectoryName', 'Defaulting Artifacts upload path to Input.', 'DataForShell')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}


