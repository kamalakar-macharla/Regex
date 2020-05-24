import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.banner.Banner
import com.manulife.logger.Logger
import com.manulife.maven.MavenPOMFile
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.versioning.IncreasePatchVersion
import com.manulife.gitlab.GitLabUtils

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        environment {
            contentFileName = null
            contentFilePath  = null
            dispatcherFileName  = null
            dispatcherFilePath  = null
            targetPath  = null
            folderName = null
        }
        tools {
            maven 'Maven 3.3.9'
            jdk 'JDK 8u112'
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
                        logger.debug("propertiesFileContentValid : ${propertiesFileContentValid}")
                        logger.debug("AEMPublisher2Credentials=${pipelineParams.AEMPublisher2Credentials}")
                        logger.debug("env.GIT_BRANCH=${env.GIT_BRANCH}")
                        localBranchName = GitLabUtils.getLocalBranchName(this)
                        unix = isUnix()
                        batsh = unix ? this.&sh : this.&bat
                        logger.debug('Environment Variables:') { batsh 'env' }
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }

                        mavenPOMFile = new MavenPOMFile(this, '')
                        mavenPOMFile.read()
                        projectVersion = mavenPOMFile.getVersion()
                    }
                }
            }
            stage('Download Package') {
                steps {
                    script {
                        try {
                            FAILED_STAGE = env.STAGE_NAME
                            def downloadLocation
                            def extension = '*.zip'
                            if (commit_id) {
                                // Artifactory
                                artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                                logger.debug('Initializing artifactoryserver')
                                ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                                logger.debug('downloading artifacts')
                                downloadLocation = artifactoryHelper.downloadArtifact(commit_id,
                                                                                      extension,
                                                                                      pipelineParams.releaseRepo,
                                                                                      pipelineParams.snapshotRepo)
                            }
                            else {
                                withCredentials([string(credentialsId: "${pipelineParams.artifactoryApiToken}", variable: 'Artifactory_Api_Token')]) {
                                    //get the latest *.zip artifact available from artifactory
                                    logger.debug("Artifactory_Api_Token:${Artifactory_Api_Token}")
                                    downloadLocation = 'artifact.downloaded'
                                    def aemEnvironment = ''
                                    if (pipelineParams.AEMEnvironment != null) {
                                        aemEnvironment = " --props 'AEMEnvironment=${pipelineParams.AEMEnvironment}'"
                                    }
                                    batsh "jfrog rt config --url=\"https://artifactory.platform.manulife.io/artifactory\" --apikey=${Artifactory_Api_Token} "
                                    if (pipelineParams.AEMExecuteMode == 'stage') {
                                        logger.debug('Downloading for Stage.')
                                        if (pipelineParams.AEMProjectMode.toUpperCase() == 'DISPATCHER') {
                                            extension = '*-stage.zip'
                                            //For Stage we need to download prod version of the package.
                                            batsh "jfrog rt dl  '${pipelineParams.releaseRepo}-local${pipelineParams.groupId}${pipelineParams.gitlabSourceRepoName}/*-prod.zip' " +
                                                      "'${downloadLocation}/'  --sort-by=created --sort-order=desc --limit=1 --flat=true ${aemEnvironment}"
                                        }
                                    batsh "jfrog rt dl  '${pipelineParams.releaseRepo}-local${pipelineParams.groupId}${pipelineParams.gitlabSourceRepoName}/${extension}' " +
                                                "'${downloadLocation}/'  --sort-by=created --sort-order=desc --limit=1 --flat=true ${aemEnvironment}"
                                    }
                                    else {
                                        logger.debug("Using pipelineParams.snapshotRepo=${pipelineParams.snapshotRepo}")
                                        logger.debug('Downloading for QA.')
                                        if (pipelineParams.AEMProjectMode.toUpperCase() == 'DISPATCHER') {
                                            extension = '*-qa.zip'
                                        }
                                        batsh "jfrog rt dl  '${pipelineParams.snapshotRepo}-local${pipelineParams.groupId}${pipelineParams.gitlabSourceRepoName}/${extension}' " +
                                            "'${downloadLocation}/'  --sort-by=created --sort-order=desc --limit=1 --flat=true ${aemEnvironment}"
                                    }
                                }
                            }
                            pipelineParams.AEMPublisher2Credentials = pipelineParams.AEMPublisher2Credentials ? pipelineParams.AEMPublisher2Credentials : pipelineParams.AEMPublisherCredentials
                            logger.info("downloaded artifacts at ${downloadLocation}")
                            logger.debug('----  Content of current Directory  ----') { batsh 'ls' }
                        }
                        catch (err) {
                            logger.error("Error:${err}", err)
                            errMsg = 'Failure'
                        }

                        boolean flag = false
                        logger.info('----  Content of data Directory  ----')
                        def folderNameList = batsh (returnStdout: true, script: 'ls -d */').split()
                        for (String folder : folderNameList) {
                            if (folder.contains('artifact')) {
                                folderName = folder
                                flag = true
                            }
                        }

                        if (flag == false) {
                            currentBuild.result = 'FAILED'
                            if (commit_id) {
                                error("No artifact found with commit_id=${commit_id}.")
                            }
                            else {
                                error('No latest artifact/zip file found.')
                            }
                        }
                    }
                }
            }
            stage('Deploy Package and Resources') {
                environment {
                    AEM_ADMIN_CREDENTIALS = credentials("${pipelineParams.AEMAdminCredentials}")
                    AEM_PUBLISHER_CREDENTIALS = credentials("${pipelineParams.AEMPublisherCredentials}")
                    AEM_AUTHOR_CREDENTIALS = credentials("${pipelineParams.AEMAuthorCredentials}")
                    AEM_PUBLISHER2_CREDENTIALS = credentials("${pipelineParams.AEMPublisher2Credentials}")
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Print Variables and Values')
                        logger.debug("${AEM_ADMIN_CREDENTIALS_USR}  ${AEM_ADMIN_CREDENTIALS_PSW}")
                        logger.debug(AEM_AUTHOR_CREDENTIALS_USR)
                        logger.debug(AEM_PUBLISHER_CREDENTIALS_USR)
                        logger.debug("WORKSPACE : ${WORKSPACE} and folderName: ${folderName}")
                        logger.debug('List of files:') { batsh "ls ${folderName}" }
                        def fileList = batsh (returnStdout: true, script: "ls ${folderName}").split()
                        logger.debug("fileList: ${fileList}")
                        boolean artifactFlag = false
                        for (String fileName : fileList) {
                            logger.debug("FileName: ${fileName}")
                            if (fileName.contains('ui.apps') &&
                                (fileName.contains(pipelineParams.AEMExecuteMode) || (!fileName.contains('dev.zip') && !fileName.contains('qa.zip') && !fileName.contains('stage.zip')))) {
                                env.apps_File_Name = fileName.replace('.zip', '')
                                env.apps_File_Path = "${WORKSPACE}/${folderName.trim()}${fileName.trim()}"
                                artifactFlag = true
                            }
                            else if (fileName.contains('ui.content') &&
                                     (fileName.contains(pipelineParams.AEMExecuteMode) || (!fileName.contains('dev.zip') && !fileName.contains('qa.zip') && !fileName.contains('stage.zip')))) {
                                contentFileName = fileName.trim()
                                contentFilePath = "${WORKSPACE}/${folderName.trim()}${fileName.trim()}"
                            }
                            //Above code is all AEM Projects where as below code is specific for dispatcher.
                            else if (fileName.contains('dispatcher') && fileName.contains(pipelineParams.AEMExecuteMode)) {
                                logger.debug("dispatcherFileName: ${fileName.trim()}")
                                dispatcherFileName = fileName.trim()
                                dispatcherFilePath =  "${WORKSPACE}/${folderName.trim()}"
                                targetPath = "${WORKSPACE}/target/".trim()
                                logger.debug("targetPath: ${targetPath.trim()}")
                                artifactFlag = true
                            }
                        }
                        if (artifactFlag == false) {
                            currentBuild.result = 'FAILED'
                            error("No artifact/zip file found. Please check if artifact downloaded correctly and infact artifact exists on artifactory with commit_id:${commit_id}.")
                        }
                        logger.debug("Deploy Stage:apps_File_Name: ${env.apps_File_Name} and contentFileName: ${contentFileName}")
                        logger.debug("dispatcherFileName: ${dispatcherFileName} and dispatcherFilePath: ${dispatcherFilePath} and targetPath: ${targetPath}")
                        logger.debug("env.apps_File_Path: ${env.apps_File_Path} and contentFilePath: ${contentFilePath}")
                        if (pipelineParams.AEMProjectMode.toUpperCase() != 'DISPATCHER' && env.apps_File_Path && env.apps_File_Name) {
                            //Upload and Install a package from File system using AEM cURL command -Author (port 4502) and Publisher (port 4503)
                            logger.debug("authDeployCommand=${pipelineParams.authDeployCommand}")
                            logger.debug("pubDeployCommand=${pipelineParams.pubDeployCommand}")
                            logger.debug("pub2DeployCommand=${pipelineParams.pub2DeployCommand}")
                            //execute shell script
                            if (pipelineParams.authDeployCommand) {
                                batsh pipelineParams.authDeployCommand.toString()
                                logger.info('Completed executing author')
                            }
                            if (pipelineParams.pubDeployCommand) {
                                batsh pipelineParams.pubDeployCommand
                                logger.info('Completed executing publisher')
                            }
                            if (pipelineParams.pub2DeployCommand) {
                                batsh pipelineParams.pub2DeployCommand
                                logger.info('Completed executing publisher-2')
                            }
                        }
                        else if (pipelineParams.AEMProjectMode.toUpperCase() == 'DISPATCHER') {
                            logger.debug("targetPath: ${targetPath}")
                            logger.debug('Folder content') { batsh 'ls' }
                            batsh "mkdir ${targetPath}"
                            logger.debug('Folder content') { batsh 'ls' }
                            executeScript = "cp -R ${dispatcherFilePath} ${targetPath}"
                            logger.debug("${WORKSPACE}")
                            logger.debug("executeScript:${executeScript}")
                            batsh executeScript
                            batsh "cd ${dispatcherFilePath}"
                            logger.debug('Folder content') { batsh 'ls' }
                            //Below code is for dispatcher code deployment.
                            if (pipelineParams.dispatcherScriptPath != null) {
                                logger.info('Start Dispatcher Script Execution')
                                if (pipelineParams.AEMDispatcherIPAddress1 != null) {
                                    logger.info("Execute Dispatcher Script for ip: ${pipelineParams.AEMDispatcherIPAddress1} and Mode: ${pipelineParams.AEMExecuteMode}")
                                    batsh "chmod +x ${pipelineParams.dispatcherScriptPath} &&  ./${pipelineParams.dispatcherScriptPath} " +
                                           "${env.BUILD_NUMBER} ${pipelineParams.AEMDispatcherIPAddress1} ${pipelineParams.AEMExecuteMode}"
                                }
                                if (pipelineParams.AEMDispatcherIPAddress2 != null) {
                                    logger.info("Execute Dispatcher Script for ip: ${pipelineParams.AEMDispatcherIPAddress2} and Mode: ${pipelineParams.AEMExecuteMode}")
                                    batsh "chmod +x ${pipelineParams.dispatcherScriptPath} &&  ./${pipelineParams.dispatcherScriptPath} " +
                                           "${env.BUILD_NUMBER} ${pipelineParams.AEMDispatcherIPAddress2} ${pipelineParams.AEMExecuteMode}"
                                }
                                logger.info('End Dispatcher Script Execution')
                            }
                        }
                    }
                }
            }
            stage('Bump Develop POM') {
                when {
                    expression {
                        return (pipelineParams.AEMExecuteMode == 'stage')
                    }
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        projectVersion = IncreasePatchVersion.perform(this, mavenPOMFile)
                    }
                }
            }
            stage('Dispatcher Clear Cache') {
                when { expression { return pipelineParams.clearCacheJenkinsJobName } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('Dispatcher Clear Cache Job Called!')
                    }
                    build job: "${pipelineParams.clearCacheJenkinsJobName}", wait: false
                }
            }
             stage('Run Promotion Pipeline') {
                when { expression { return pipelineParams.promotionPipelineJobName } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('Promotion Pipeline Job Called!')
                    }
                    build job: "${pipelineParams.promotionPipelineJobName}", wait: false
                }
            }
        }
        post {
            always {
                cleanWs()
                script {
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
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
            string(
                name: 'commit_id',
                defaultValue: '',
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
    return 'ssh ' + getSSHOptions(pipelineParams) + " ${pipelineParams.user_name}@${pipelineParams.edge_node} "
}

def getSSHOptions(Properties pipelineParams) {
    return "-i ${pipelineParams.identity_file} -o StrictHostKeyChecking=no"
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()
    propertiesCatalog.addOptionalProperty('projectType', 'Defaulting projectType to null. This property will hold the type of project.', null)
    propertiesCatalog.addOptionalProperty('appFEComponentGitLocation', 'Defaulting appFEComponentGitLocation property to null.', null)
    propertiesCatalog.addOptionalProperty('appFEComponentGitBranch', 'Defaulting appFEComponentGitBranch property to master.', 'master')
    propertiesCatalog.addOptionalProperty('appFEScriptsSource', 'Defaulting appFEScriptsSource property to null.', null)
    propertiesCatalog.addOptionalProperty('appFEScriptsTarget', 'Defaulting appFEScriptsTarget property to null.', null)
    propertiesCatalog.addOptionalProperty('appFEStylesSource', 'Defaulting appFEStylesSource property to null.', null)
    propertiesCatalog.addOptionalProperty('appFEStylesTarget', 'Defaulting appFEStylesTarget property to null.', null)
    propertiesCatalog.addMandatoryProperty('AEMAdminCredentials', '[ERROR]: Missing AEMAdminCredentials property value from Jenkins Credentials.')
    propertiesCatalog.addMandatoryProperty('AEMAuthorCredentials', '[ERROR]: Missing AEMAuthorCredentials property value from Jenkins Credentials.')
    propertiesCatalog.addMandatoryProperty('AEMPublisherCredentials', '[ERROR]: Missing AEMPublisherCredentials property value from Jenkins Credentials.')
    propertiesCatalog.addOptionalProperty('AEMPublisher2Credentials', 'Defaulting AEMPublisher2Credentials property to null', null)
    propertiesCatalog.addOptionalProperty('authDeployCommand', 'Missing authDeployCommand build commands.', null)
    propertiesCatalog.addOptionalProperty('pubDeployCommand', 'Missing pubDeployCommand build commands.', null)
    propertiesCatalog.addOptionalProperty('pub2DeployCommand', 'Missing pub2DeployCommand build commands.', null)
    propertiesCatalog.addOptionalProperty('dispatcherScriptPath', 'Used to deploy dispatcher code. Defaulting dispatcherScriptPath property to null.', null)
    propertiesCatalog.addOptionalProperty('AEMDispatcherIPAddress1', 'This property holds the AEM Dispatcher IP address. Defaulting AEMDispatcherIPAddress1 property to null.', null)
    propertiesCatalog.addOptionalProperty('AEMDispatcherIPAddress2', 'This property holds the AEM Dispatcher IP address. Defaulting AEMDispatcherIPAddress2 property to null.', null)
    propertiesCatalog.addOptionalProperty('AEMExecuteMode',
                                          'This property holds the mode in which job is running. Like running on dev, qa or stage modes. Defaulting AEMExecuteMode property to null.',
                                          null)
    propertiesCatalog.addOptionalProperty('groupId', 'This property holds the sub-folder where the artifacts are stored. Defaulting groupId property to null.', null)
    propertiesCatalog.addOptionalProperty('clearCacheJenkinsJobName',
                                          'Defaulting clearCacheJenkinsJobName to null.' +
                                            'Could be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this pipelines.',
                                          null)
    propertiesCatalog.addOptionalProperty('gitlabSourceRepoName', 'Defaulting gitlabSourceRepoName to null. This property will hold the name of package.', null)
    propertiesCatalog.addMandatoryProperty('AEMProjectMode', '[ERROR]: Missing AEMProjectMode property value. Possible values are global, dispatcher, rr, cpao.')
    propertiesCatalog.addOptionalProperty('AEMEnvironment', 'Defaulting AEMEnvironment to null. This property will hold the name of environment ENV1/ENV2.', null)
    propertiesCatalog.addOptionalProperty('promotionPipelineJobName', 'Defaulting promotionPipelineJobName to null. This property will hold the path of the promotion pipeline we want to run.', null)
    propertiesCatalog.addOptionalProperty('bumpPomBranch', 'Defaulting bumpPomBranch to null. This property hold the value of branch which pom version need to be bumped.', null)
    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_MAVEN)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}