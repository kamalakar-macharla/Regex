import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
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

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }
                    }
                }
            }
            stage('Download Package') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def repo = env.GIT_BRANCH.matches('^.*dev$') ? pipelineParams.snapshotRepo : pipelineParams.releaseRepo
                        def downloadSpec =
                            """{
                                "files":
                                    [{
                                        "pattern": "${repo}/${pipelineParams.downloadPattern}",
                                        "flat": "true",
                                        "props" : "vcs.revision=${params.commit_id}",
                                        "target": "./${pipelineParams.downloadedFileName}"
                                    }]
                            }"""
                        logger.debug("downloadSpec: ${downloadSpec}")
                        def server = Artifactory.server pipelineParams.artifactoryInstance
                        server.download(downloadSpec)
                    }
                }
            }
            stage ('Copy Package on Edge Node') {
                steps {
                    FAILED_STAGE = env.STAGE_NAME
                    logger.debug("Creating temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")
                    sh getSSHCommand(pipelineParams) + " \"${pipelineParams.context_commands} mkdir ${pipelineParams.home_folder}/${JOB_BASE_NAME}\""

                    logger.debug('Copying package to edge node')
                    sh "scp ${getSSHOptions(pipelineParams)} ${pipelineParams.downloadedFileName} " +
                        "${pipelineParams.user_name}@${pipelineParams.edge_node}:${pipelineParams.home_folder}/${JOB_BASE_NAME}"
                }
            }
            stage('Deploy application on Hadoop') {
                steps {
                    FAILED_STAGE = env.STAGE_NAME
                    sh "${getSSHCommand(pipelineParams)} \"${pipelineParams.context_commands} cd ${pipelineParams.home_folder}/${JOB_BASE_NAME} && ${pipelineParams.deploy_command}\""
                }
            }
        }
        post {
            always {
                logger.debug("Deleting temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")
                sh "${getSSHCommand(pipelineParams)} \"${pipelineParams.context_commands} rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}\""

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
            string(
                name: 'commit_id',
                defaultValue: 'MISSING_COMMIT_ID',
                description: 'Commit id of the package to be downloaded from Artifactory'
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

def getSSHCommand(Properties pipelineParams) {
    return "ssh ${getSSHOptions(pipelineParams)} ${pipelineParams.user_name}@${pipelineParams.edge_node} "
}

def getSSHOptions(Properties pipelineParams) {
    return "-i ${pipelineParams.identity_file} -o StrictHostKeyChecking=no"
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addMandatoryProperty('edge_node', 'Missing the edge node name.')
    propertiesCatalog.addMandatoryProperty('identity_file', 'Missing the location (path + name) of the SSH identity file for the user to be used on the edge node.')
    propertiesCatalog.addMandatoryProperty('user_name', 'Missing the name of the user to be used on the edge node.')
    propertiesCatalog.addMandatoryProperty('home_folder', 'Missing the home folder of the user on the edge node.')
    propertiesCatalog.addMandatoryProperty('deploy_command', 'Missing the command used to deploy the package on Hadoop.')
    propertiesCatalog.addMandatoryProperty('downloadedFileName', 'Name the downloaded file will have on disk.')
    propertiesCatalog.addMandatoryProperty('downloadPattern', 'Filter on file name to be downloaded.')
    propertiesCatalog.addOptionalProperty('context_commands',
                                          'Linux commands that provide some context about the location where the actions are taken.  Defaults to \"hostname && pwd &&\".  ' +
                                            'Could be assigned an empty string is not desired.',
                                          'hostname && pwd && ')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_MAVEN)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}