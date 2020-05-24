import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactGovernance
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.gradle.BuildGradleFile
import com.manulife.logger.Logger
import com.manulife.microsoft.ProjectName
import com.manulife.provisioning.ProvisioningRun
import com.manulife.provisioning.ProvisioningCLI
import com.manulife.provisioning.Request
import com.manulife.provisioning.BuildManifest
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.Shell

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.agent}"
        }
        environment {
            propertiesFileName = null
            postGatingResults = null
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
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }

                        // This is required for building the artifact when calling updateRequestGatingResults
                        propertiesFileName = configuration.propertiesFileName

                        //Check to see if a SNOW ticket is provided, and whether the deployment is to production
                        if (pipelineParams.org.contains('PROD')) {
                            if (pipelineParams.ticketNumber == null) {
                                error('[ERROR]: ServiceNow Change Ticket is Required When Deploying to Production')
                            }
                            else {
                                logger.info(
                                    '###########################################################################################################\n' +
                                    "ServiceNow Change Ticket Number: ${pipelineParams.ticketNumber}\n" +
                                    '###########################################################################################################\n')
                            }
                        }
                        else {
                            logger.info(
                                 '###########################################################################################################\n' +
                                 'ServiceNow Change Ticket Not Required For Non-Production Environments\n' +
                                 '###########################################################################################################\n')
                        }

                        //Check to see if the disaster recovery option was selected
                        if (params.disasterRecovery == true) {
                            pipelineParams.foundation = 'CAE'
                            logger.info('###########################################################################################################\n' +
                                        'Deployment is being run in a disaster recovery region\n' +
                                        '###########################################################################################################\n')
                        }

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)
                        batsh = unix ? this.&sh : this.&bat

                        logger.info("Received the following value for the commit_version parameter: ${params.commit_version}")

                        //READ MANIFEST YAML / API Request Object
                        request = new Request()
                        buildManifest = new BuildManifest(this)
                        buildManifest.readManifestFile()
                        request.buildpack = buildManifest.getBuildPack()
                        request.applicationName = buildManifest.getAppName()
                        logger.info("DETECTED BUILDPACK: ${request.buildpack}")
                        logger.info("DETECTED APPLICATION NAME: ${request.applicationName}")
                        request.action = params.action

                        //Dectect App Language
                        request.language = 'default'
                        if (pipelineParams.releaseRepo.contains('npm')) {
                            request.language = (request.buildpack.contains('static') ? 'html' : 'node')
                        }
                        else if (pipelineParams.releaseRepo.contains('maven')) {
                            request.language = 'javaMaven'

                            //Check if the java app is built using gradle, change the language type if true
                            if (fileExists('build.gradle') || fileExists('build.gradle.kts')) {
                                request.language = 'javaGradle'
                            }
                        }
                        else if (pipelineParams.releaseRepo.contains('nuget')) {
                            request.language = 'dotnetcore'
                        }
                        else {
                            error('APPLICATION LANGUAGE COULD NOT BE DETECTED')
                        }

                        logger.info("DETECTED LANGUAGE: ${request.language}")

                        //Artifact Governance Object
                        artifact = new ArtifactGovernance()
                        gatingOverride = pipelineParams.gatingOverride
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage ('Download Binary') {
                when { expression { return (pipelineParams.releaseRepo) && ('deploy' == params.action)  } }
                environment {
                    ARTIFACTORY_SA_TOKEN = credentials("${pipelineParams.artifactoryTokenCredId}")
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.downloadBinaryStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        //Check that the application is in a running state or the deployment will fail
                        if (!ProvisioningRun.appinfo(this, request)) {
                            error('The targeted PCF Application is either in a stopped or crashed state. Please fix the application before running the deployment')
                        }

                        logger.debug('jfrog version: ') { batsh 'jfrog -v' }
                        //Make sure the API is configured. We might want to do this a different way later on
                        batsh "jfrog rt config --url=https://artifactory.platform.manulife.io/artifactory --apikey=${ARTIFACTORY_SA_TOKEN} art-global"

                        //Check whether a commit ID or a version number was passed in
                        boolean commitVersion = params.commit_version =~ /\./

                        if (request.language == 'node' || request.language == 'html') {
                            //Get package info and metadata info
                            def packageName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
                            def metaData = ArtifactoryHelper.getMetaData("${pipelineParams.releaseRepo}/${packageName}/*.tgz",
                                                                        commitVersion,
                                                                        params.commit_version,
                                                                        this)

                            //Update artifact governance object with open source governance results
                            artifact = ArtifactoryHelper.updateRequestGatingResults(this, pipelineParams, metaData, artifact)
                            if (artifact.deploymentOverride == true) {
                                currentBuild.result = 'UNSTABLE'
                            }

                            //Download package from artifactory
                            ArtifactoryHelper.downloadArtifactByCLI(this,
                                                                    params.commit_version,
                                                                    "${pipelineParams.releaseRepo}/${packageName}/*.tgz",
                                                                    "${packageName}.tgz",
                                                                    commitVersion)

                        }
                        else if (request.language == 'javaMaven') {
                            //Get jar file info and metadata info
                            def mavenArtifactId = readMavenPom().getArtifactId()
                            def mavenGroupId = readMavenPom().getGroupId()
                            mavenGroupId = mavenGroupId.replaceAll(/\./, /\//)
                            def metaData = ArtifactoryHelper.getMetaData("${pipelineParams.releaseRepo}/${mavenGroupId}/${mavenArtifactId}/*.jar",
                                                                        commitVersion,
                                                                        params.commit_version,
                                                                        this)

                            //Update artifact governance object with open source governance results
                            artifact = ArtifactoryHelper.updateRequestGatingResults(this, pipelineParams, metaData, artifact)
                            if (artifact.deploymentOverride == true) {
                                currentBuild.result = 'UNSTABLE'
                            }
                            ArtifactoryHelper.downloadArtifactByCLI(this,
                                                                    params.commit_version,
                                                                    "${pipelineParams.releaseRepo}/${mavenGroupId}/${mavenArtifactId}/*.jar",
                                                                    "package/${mavenArtifactId}.jar",
                                                                    commitVersion)
                        }
                        else if (request.language == 'javaGradle') {
                            BuildGradleFile buildGradleFile = new BuildGradleFile(this, false)
                            //Get info on the app from the build.gradle file
                            def gradleGroupId = buildGradleFile.getGroup()
                            def gradleArtifactId = buildGradleFile.getJarBaseName()

                            //Fix the group id and get the artifacts meta data
                            gradleGroupId = gradleGroupId.replaceAll(/\./, /\//)
                            def metaData = ArtifactoryHelper.getMetaData(
                                                                        "${pipelineParams.releaseRepo}/${gradleGroupId}/${gradleArtifactId}/*.jar",
                                                                        commitVersion,
                                                                        params.commit_version,
                                                                        this)

                            //Update artifact governance object with open source governance results
                            artifact = ArtifactoryHelper.updateRequestGatingResults(this, pipelineParams, metaData, artifact)
                            if (artifact.deploymentOverride == true) {
                                currentBuild.result = 'UNSTABLE'
                            }

                            //Download the jar file and pom file
                            ArtifactoryHelper.downloadArtifactByCLI(this,
                                                                    params.commit_version,
                                                                    "${pipelineParams.releaseRepo}/${gradleGroupId}/${gradleArtifactId}/*.jar",
                                                                    "package/${gradleArtifactId}.jar",
                                                                    commitVersion)

                            ArtifactoryHelper.downloadArtifactByCLI(this,
                                                                    params.commit_version,
                                                                    "${pipelineParams.releaseRepo}/${gradleGroupId}/${gradleArtifactId}/*.pom",
                                                                    'pom.xml',
                                                                    commitVersion)
                        }
                        else if (request.language == 'dotnetcore') {
                            ProjectName.fix('projectName', pipelineParams)
                            publishName = pipelineParams.projectDeliverableName ?: pipelineParams.projectName
                            def metaData
                            logger.info("Publish Name: ${publishName}")

                            //TODO once a way is determined on how to read the csproj values
                            //Get metadata based on the provided commit ID or version, if no commit ID or version is provided then it will get
                            if (params.commit_version == '' && commitVersion == false) {
                                metaData = sh(returnStdout: true, script: "jfrog rt s ${pipelineParams.releaseRepo}/${publishName}/*.nupkg --sort-by=\"created\" --sort-order=\"desc\" --limit=\"1\"").trim()
                            }
                            else if (commitVersion == true) {
                                metaData = sh(returnStdout: true, script: "jfrog rt s ${pipelineParams.releaseRepo}/${publishName}/*.nupkg --props=\"artifact.version=\"${params.commit_version} ").trim()
                            }
                            else if (commitVersion == false && params.commit_version != '') {
                                metaData = sh(returnStdout: true, script: "jfrog rt s ${pipelineParams.releaseRepo}/${publishName}/*.nupkg --props=\"vcs.revision=\"${params.commit_version} ").trim()
                            }
                            logger.info(metaData)

                            //Update artifact governance object with open source governance results
                            artifact = ArtifactoryHelper.updateRequestGatingResults(this, pipelineParams, metaData, artifact)
                            if (artifact.deploymentOverride == true) {
                                currentBuild.result = 'UNSTABLE'
                            }

                            //Download zip from artifactory
                            if (params.commit_version == '' && commitVersion == false) {
                                batsh "jfrog rt dl ${pipelineParams.releaseRepo}/${publishName}/*.nupkg ${publishName}.nupkg --flat=\"true\" --sort-by=\"created\" --sort-order=\"desc\" --limit=\"1\" "
                            }
                            else if (commitVersion == true) {
                                batsh "jfrog rt dl ${pipelineParams.releaseRepo}/${publishName}/*.nupkg ${publishName}.nupkg --flat=\"true\" --props=\"artifact.version=\"${params.commit_version} "
                            }
                            else if (commitVersion == false && params.commit_version != '') {
                                batsh "jfrog rt dl ${pipelineParams.releaseRepo}/${publishName}/*.nupkg ${publishName}.nupkg --flat=\"true\" --props=\"vcs.revision=\"${params.commit_version} "
                            }
                        }

                        //String array to make the artifact governance gating results persistance
                        postGatingResults = new String[4]
                        postGatingResults[0] = artifact.sonarQubeScanMsg
                        postGatingResults[1] = artifact.blackDuckScanMsg
                        postGatingResults[2] = artifact.snykScanMsg
                        postGatingResults[3] = artifact.fortifyScanMsg
                        stagesExecutionTimeTracker.downloadBinaryStageEnd()
                    }
                }
            }
            stage ('Prepare Request') {
                when { expression { return (pipelineParams.releaseRepo) && ('deploy' == params.action && pipelineParams.unitTestFileName == null) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.prepareRequestStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        zipUpArtifacts()
                        stagesExecutionTimeTracker.prepareRequestStageEnd()
                    }
                }
            }
            stage ('Deploy With Unit Tests') {
                when { expression { return (pipelineParams.releaseRepo) && ('deploy' == params.action && pipelineParams.unitTestFileName != null) } }
                steps {
                    script {
                        if (fileExists("${pipelineParams.unitTestFileName}")) {
                            //Setup the provisioning CLI
                            ProvisioningCLI.configureProvisioningCLI(this)

                            //Build deployment command
                            def pushCommand = ProvisioningCLI.buildPushCommand(this, request)

                            //Package the application
                            ProvisioningCLI.packageApplication(this, request)

                            //Execute deployment command
                            batsh "${pushCommand}"
                        }
                        else {
                            error('ERROR: The specified unit-test case file does not exist in the workspace')
                        }
                    }
                }
            }
            stage ('Manage Service Dependencies') {
                when { expression { return (pipelineParams.servicesFileName) && ('service' == params.action) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.manageServiceDependenciesStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        if (fileExists("${pipelineParams.servicesFileName}")) {
                            request.serviceId = ProvisioningRun.service(this, request)
                            if (request.serviceId == null) {
                                currentBuild.result = 'FAILED'
                            }

                            request.serviceStatus = ProvisioningRun.status(this, request.serviceId)
                        }
                        else {
                            logger.info("Skipped the service creation as the ${pipelineParams.servicesFileName} file doesn't exist")
                        }
                        stagesExecutionTimeTracker.manageServiceDependenciesStageEnd()
                    }
                }
            }
            stage ('Submit Request to API') {
                when { expression { return (pipelineParams.provTeamTokenCredId) && ('service' != params.action) && pipelineParams.unitTestFileName == null } }
                steps {
                    script {
                        stagesExecutionTimeTracker.submitRequestApiStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        request.id = ProvisioningRun.request(this, request)
                        if (!request.id) {
                             error('ERROR: The Provisoning API did not provide a transaction ID')
                        }
                        stagesExecutionTimeTracker.submitRequestApiStageEnd()
                    }
                }
            }
            stage ('Monitor Progress') {
                when { expression { return ('service' != params.action) && (pipelineParams.unitTestFileName == null) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.monitorProgressStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        request.status = ProvisioningRun.status(this, request.id)
                        if (request.status == 'FAILED' || request.status == 'TIMEOUT' ) {
                            error('Provisoning API Deployment Failed')
                        }
                        stagesExecutionTimeTracker.monitorProgressStageEnd()
                    }
                }
            }
            stage ('Log Binary Status') {
                when { expression { return (pipelineParams.releaseRepo) && (params.commit_version) && ('deploy' == params.action) } }
                environment {
                    ARTIFACTORY_SA_TOKEN = credentials("${pipelineParams.artifactoryTokenCredId}")
                }
                steps {
                    script {
                        //Check whether a commit ID or a version number was passed in
                        FAILED_STAGE = env.STAGE_NAME
                        boolean commitVersion = params.commit_version =~ /\./
                        def packageName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
                        batsh "jfrog rt config --url=https://artifactory.platform.manulife.io/artifactory --apikey=${ARTIFACTORY_SA_TOKEN} art-global"

                        def spCmdLine = "jfrog rt sp ${pipelineParams.releaseRepo}/${packageName}/*.tgz \"${pipelineParams.space}=DEPLOYED\" "
                        if (params.commit_version == '' && commitVersion == false) {
                            batsh spCmdLine + '--sort-by=\"created\" --sort-order=\"desc\" --limit=\"1\" '
                        }
                        else if (commitVersion == true) {
                            batsh spCmdLine + "--props=\"artifact.version=\"${params.commit_version} "
                        }
                        else if (commitVersion == false && params.commit_version != '') {
                            batsh spCmdLine + "--props=\"vcs.revision=\"${params.commit_version} "
                        }
                    }
                }
            }
            stage('Apply Scaling Rules') {
                when { expression { return (pipelineParams.provTeamTokenCredId) && (null != pipelineParams.scalingFileName) && ('deploy' == params.action) } }
                steps {
                    script {
                        if (fileExists("${pipelineParams.scalingFileName}")) {
                            request.action = 'autoscaler'
                            request.id = ProvisioningRun.request(this, request)
                            if (request.id == null) {
                                error('ERROR: The Provisoning API did not provide a transaction ID')
                            }

                            request.status = ProvisioningRun.status(this, request.id)

                            if (request.status == 'FAILED' || request.status == 'TIMEOUT' ) {
                                error('Provisoning API failed to apply autoscaling rules')
                            }
                        }
                        else {
                            error("The auto scaling rule creation was not completed because the ${pipelineParams.scalingFileName} file doesn't exist")
                        }
                    }
                }
            }
            stage('Trigger Next Pipeline') {
                when { expression { return pipelineParams.nextJobName } }
                steps {
                    build job: "${pipelineParams.nextJobName}",
                    wait: false
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    logger.info(
                    '###########################################################################################################\n' +
                    'Result \n' +
                    "Action: ${params.action}\n" +
                    "Request ID: ${request.id}\n" +
                    "Request Status: ${request.status}\n" +
                    '###########################################################################################################\n')

                    if (request.serviceId) {
                        logger.info(
                        '###########################################################################################################\n' +
                        'Marketplace Services \n' +
                        "Service ID: ${request.serviceId}\n" +
                        "Service Status: ${request.serviceStatus}\n" +
                        '###########################################################################################################\n')
                    }
                        if (request.serviceId) {
                            logger.info(
                            '###########################################################################################################\n' +
                            'Marketplace Services \n' +
                            "Service ID: ${request.serviceId}\n" +
                            "Service Status: ${request.serviceStatus}\n" +
                            '###########################################################################################################\n')
                        }

                        if (params.action == 'deploy') {
                            //TODO: add erroring if the artifact is not in a good state with scans
                            logger.info(
                            '###########################################################################################################\n' +
                            'Artifact Gating \n' +
                            "Code Quality Gate: ${postGatingResults[0]}\n" +
                            "Open-Source Governance Gate (BlackDuck): ${postGatingResults[1]}\n" +
                            "Open-Source Governance Gate (Snyk): ${postGatingResults[2]}\n" +
                            "Code Security Gate: ${postGatingResults[3]}\n" +
                            '###########################################################################################################\n')
                        }

                    if (params.loggingLevel == 'DEBUG') {
                        archiveArtifacts artifacts: '*.zip', allowEmptyArchive: true
                    }

                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()
                    cleanWs()
                }
            }
        }
        parameters {
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
            choice(
                name: 'action',
                choices: ['deploy', 'service', 'start', 'stop', 'restart', 'delete'],
                description: 'Action to be taken for the Provisioning Service'
            )
            string(
                name: 'commit_version',
                defaultValue: '',
                description: 'Git Commit hash or artifact version number, ' +
                              'used to search artifactory for an exact binary match. If left empty Jenkins will deploy the latest version found in Artifactory.'
            )
            string(
                name: 'change_ticket',
                defaultValue: '',
                description: 'ServiceNow approved change tickets for production releases'
            )
            booleanParam(
                name: 'disasterRecovery',
                defaultValue: false,
                description: 'If this is set to true the deployment will be run in a Disaster Recovery (DR) Region'
            )
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
    }
}

def zipUpArtifacts() {

    //Zip up the appropriate artifacts based on the request.language/project type
    if (request.language == 'node') {
        def packageName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
        batsh "tar -xzf ${packageName}.tgz"
        batsh "rm ${packageName}.tgz"
        batsh "cat ${pipelineParams.manifestFileName}"
        if (fileExists('package/.scannerwork')) {
            batsh 'rm -r package/.scannerwork'
        }
        batsh 'ls -la package/'
        batsh "cp -R ${pipelineParams.manifestFileName} package"
        batsh 'cp -R .npmrc package'
        batsh "zip -r ${packageName}.zip package >/dev/null"
        request.fileName = "${packageName}.zip"
    }
    else if (request.language == 'html') {
        def packageName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
        batsh "tar -xzf ${packageName}.tgz"
        batsh "rm ${packageName}.tgz"
        batsh "cat package/${pipelineParams.manifestFileName}"
        if (fileExists('package/.scannerwork')) {
            batsh 'rm -r package/.scannerwork'
        }
        batsh 'ls -la package/'
        batsh "zip -r ${packageName}.zip package >/dev/null"
        request.fileName = "${packageName}.zip"
    }
    else if (request.language == 'javaMaven' || request.language == 'javaGradle') {
        def mavenArtifactId = readMavenPom().getArtifactId()
        batsh "cat ${pipelineParams.manifestFileName}"
        batsh 'ls -la package/'
        batsh "cp -R ${pipelineParams.manifestFileName} package"
        batsh "zip -r ${mavenArtifactId}.zip package >/dev/null"
        request.fileName = "${mavenArtifactId}.zip"
    }
    else if (request.language == 'dotnetcore') {
        batsh "cat ${pipelineParams.manifestFileName}"
        batsh "unzip ${publishName}.nupkg -d package"
        batsh "cp -R ${pipelineParams.manifestFileName} package"
        batsh 'cp -R package/publish/* package'
        if (fileExists('package/.scannerwork')) {
            batsh 'rm -r package/.scannerwork'
        }
        batsh 'ls -la package/'
        batsh "zip -r ${publishName}.zip package >/dev/null"
        batsh 'ls -la'
        request.fileName = "${publishName}.zip"
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addOptionalProperty('artifactoryTokenCredId', 'Defaulting artifactoryTokenId property to artifactoryAPIToken', 'artifactoryAPIToken')
    propertiesCatalog.addMandatoryProperty('provTeamTokenCredId', 'Missing the provisioning token credential ID property Example: ACL_APP_TEAM_CDT-EXAMPLES')
    propertiesCatalog.addOptionalProperty('foundation', 'PCF foundation options: USE (for Sandbox), CAC (for Preview or Operations), ' +
                                                         'CAE (for CDN DR), SEA (for ASIA Preview), EAS (for ASIA Operations)', 'CAC')
    propertiesCatalog.addMandatoryProperty('org', 'Missing the PCF Org (org) Example: CDN-CAC-DEV')
    propertiesCatalog.addMandatoryProperty('space', 'Missing the PCF Space (space) Example: EXAMPLE-CAC-DEV')
    propertiesCatalog.addMandatoryProperty('manifestFileName', 'Missing the PCF manifest file name for the specific environment Example: manifest-tst.yml')
    propertiesCatalog.addOptionalProperty('servicesFileName', 'Missing the Services file name to create PCF services for the specific environment, Example: services-dev.json', null)
    propertiesCatalog.addOptionalProperty('scalingFileName', 'Missing the Auto Scaler info file name to define the rules for how the app should be scaled, Example: autoscaling-dev.json', null)
    propertiesCatalog.addOptionalProperty('unitTestFileName', 'Missing the unit test batch script file for blue green deployments with unit testing, Example: unit-test-dev.sh', null)
    propertiesCatalog.addMandatoryProperty('releaseRepo', 'Missing the release repo path from artifactory')
    propertiesCatalog.addOptionalProperty('nextJobName', 'Defaulting nextJobName property to null.', null)
    propertiesCatalog.addOptionalProperty('gatingOverride', 'Overrides Artifactory gating checks when deploying to UAT and Production', 'false')
    propertiesCatalog.addOptionalProperty('servicePrivateKey', 'Jenkins credential ID for a SSH private key that will be injected during service creation', null)

    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}
