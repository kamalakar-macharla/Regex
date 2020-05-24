import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.artifactory.ArtifactGovernance
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
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker


def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        environment {
            downloadLocation = 'artifact.download/'
            artifactName = null
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
                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)
                        logger.debug("propertiesFileContentValid : ${propertiesFileContentValid}")
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }

                        // This is required for building the artifact when calling updateRequestGatingResults
                        propertiesFileName = configuration.propertiesFileName

                        unix = isUnix()
                        batsh = unix ? this.&sh : this.&bat
                        logger.debug('EnvironmentVariables: ') { batsh 'env' }
                        collections = []
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Download Package') {
                environment {
                    ARTIFACTORY_SA_TOKEN = credentials("${pipelineParams.artifactoryApiToken}")
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.downloadBinaryStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        // Fetching properties for downloading proper Artifact
                        // Process of downloading Artifact using JFrog - CLI
                        try {
                            batsh "jfrog rt config --url=https://artifactory.platform.manulife.io/artifactory --apikey=${ARTIFACTORY_SA_TOKEN} art-global"

                            boolean commitVersion = params.commit_version =~ /\./
                            ArtifactoryHelper.downloadArtifactByCLI(this,
                                                                    params.commit_version,
                                                                    "${pipelineParams.releaseRepo}/${pipelineParams.artifactLocation}/${pipelineParams.artifactExtension}",
                                                                    downloadLocation,
                                                                    commitVersion)

                            logger.info("Downloaded artifacts to ${downloadLocation}")
                            logger.debug('----  Content of the Artifactory Directory ----') { batsh "ls ${downloadLocation}" }

                            logger.debug(params.toString())
                            logger.debug(params.commit_version.toString())
                            def metaData = ArtifactoryHelper.getMetaData("${pipelineParams.artifactExtension}",
                                                                        commitVersion,
                                                                        params.commit_version,
                                                                        this)
                            logger.debug(metaData)
                            //Artifact Governance Object
                            artifact = new ArtifactGovernance()
                            //Update artifact governance object with open source governance results
                            artifact = ArtifactoryHelper.updateRequestGatingResults(this, pipelineParams, metaData, artifact)
                            if (artifact.deploymentOverride == true) {
                                currentBuild.result = 'UNSTABLE'
                            }

                        }
                        catch (err) {
                            logger.error("Error:${err}", err)
                        }
                        // Validating the Download
                        boolean folderFound = false
                        logger.info('----  Content of data Directory  ----')
                        def folderNameList = batsh (returnStdout: true, script: 'ls -d */').split()
                        for (String folder : folderNameList) {
                            if (folder.contains('artifact')) {
                                folderName = folder
                                folderFound = true
                                dir("${folderName}") {
                                    artifactName = batsh (returnStdout: true, script: 'ls')
                                }
                            }
                        }
                        logger.debug("Downloaded Artifact Name: ${artifactName}")
                        if (folderFound == false) {
                            currentBuild.result = 'FAILED'
                            if (commit_version) {
                                error("No artifact found with commit_version=${commit_version}.")
                            }
                            else {
                                error('No latest artifact/zip file found.')
                            }
                        }
                        stagesExecutionTimeTracker.downloadBinaryStageEnd()
                    }
                }
            }
            stage ('Copy Package on Edge Node') {
                steps {
                    // Copying artifacts from Jenkins Build Machine Workspace to Edge Node
                    script {
                        stagesExecutionTimeTracker.prepareRequestStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        // Console logs to see the values of parameters -- Both for info and debug mode
                        logger.info('Copying package to edge node -- Using scp protocol')
                        logger.info('==============================================================')
                        logger.info("Edge node: ${pipelineParams.edge_node} ")
                        logger.info("Username: ${pipelineParams.username} ")
                        logger.info("Package Destination path: ${pipelineParams.deployDirLocation}")
                        logger.info("Package Name: ${artifactName}")
                        logger.info("Configuration file names: ${pipelineParams.applicationConfigFiles}")
                        logger.info('Deployment in progress ...')
                        logger.info('==============================================================')
                        // Command that transfers files (sshpass and scp) -- NonInterative SSH with secure copy
                        withCredentials([usernamePassword(credentialsId: "${pipelineParams.userCredentials}", usernameVariable: 'Username', passwordVariable: 'Password')]) {
                            batsh "sshpass -v -p ${Password} scp -o StrictHostKeyChecking=no ${downloadLocation}/* " +
                                   "${pipelineParams.username}@${pipelineParams.edge_node}:${pipelineParams.deployDirLocation}"
                            try {
                                if (pipelineParams.applicationConfigFiles != null) {
                                    String[] appFiles= "${pipelineParams.applicationConfigFiles}".split(",")
                                    for (String appFile : appFiles) {
                                        withCredentials([file(credentialsId: appFile, variable: 'secret')]) {
                                            batsh "sshpass -v -p ${Password} scp $secret ${pipelineParams.username}@${pipelineParams.edge_node}:${pipelineParams.deployDirLocation} "
                                        }
                                    }
                                    batsh "sshpass -v -p ${Password} ssh -o StrictHostKeyChecking=no ${pipelineParams.username}@${pipelineParams.edge_node} " +
                                        "attrib -r ${pipelineParams.deployDirLocation}/* /s "
                                    }
                                }
                            catch (err) {
                                logger.error("Error:${err}", err)
                            }
                            logger.info('Deploy Success !!!')
                        }
                        stagesExecutionTimeTracker.prepareRequestStageEnd()
                    }
                }
            }
            stage('Post Deployment Script') {
                when { expression { return pipelineParams.supportScriptCommand } }
                steps {
                    script {
                        PipelineRunAuditTrailing.log(this)
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('==============================================================')
                        logger.info('POST DEPLOYMENT SCRIPT')
                        logger.info("Script      ${pipelineParams.supportScriptCommand} ")
                        logger.info("Argument 1  ${pipelineParams.deployDirLocation} ")
                        logger.info("Argument 2  ${artifactName}")
                        logger.info('==============================================================')
                        // TO-DO -- Consodering a proper support for Windows Edege Nodes ... ?
                        withCredentials([usernamePassword(credentialsId: "${pipelineParams.userCredentials}", usernameVariable: 'Username', passwordVariable: 'Password')]) {
                        batsh "sshpass -v -p ${Password} ssh -o StrictHostKeyChecking=no ${pipelineParams.username}@${pipelineParams.edge_node} " +
                               "${pipelineParams.supportScriptCommand} ${pipelineParams.deployDirLocation} ${artifactName}"
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
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
                name: 'commit_version',
                defaultValue: '',
                description: 'Git Commit hash or artifact version number, ' +
                              'used to search artifactory for an exact binary match. If left empty Jenkins will deploy the latest version found in Artifactory.'
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
    propertiesCatalog.addMandatoryProperty('edge_node', 'Missing the edge node name.')
    propertiesCatalog.addMandatoryProperty('userCredentials', 'Missing the location (path + name) of the SSH identity file for the user to be used on the edge node.')
    propertiesCatalog.addMandatoryProperty('username', 'Missing the name of the user to be used on the edge node.')
    propertiesCatalog.addOptionalProperty('artifactoryApiToken', 'Defaulting artifactoryApiToken property to artifactoryAPIToken', 'artifactoryAPIToken')
    propertiesCatalog.addMandatoryProperty('artifactLocation', 'This parameter defines path to the project folder in the artifactory. ' +
                                                                 'Exclude the artifact repo name at starting of the path (E.g. for path mfc-dig-pypi-local/affluence_ind : ' +
                                                                 'exclude mfc-dig-pypi-local/ i.e. the repo name).')
    propertiesCatalog.addMandatoryProperty('artifactExtension', 'This parameter defines the file extension of the artifact you want to download. You may use some wildcards and extension as per need.')
    propertiesCatalog.addOptionalProperty('applicationConfigFiles', 'This parameter defines the application configuration files to be copied to the edge node and they have to be comma separated with no spaces ', null)
    propertiesCatalog.addOptionalProperty('deployDirLocation', 'Defaulting deployDirLocation property to null', null)
    propertiesCatalog.addOptionalProperty('supportScriptCommand', 'Defaulting supportScriptCommand property to null', null)


    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.EDGE_DEPLOY)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}