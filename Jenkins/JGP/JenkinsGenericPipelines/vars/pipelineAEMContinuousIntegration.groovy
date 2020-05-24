import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.blackduck.BlackduckPropertiesCalalogBuilder
import com.manulife.blackduck.BlackDuckRunner
import com.manulife.blackduck.BlackDuckResult
import com.manulife.fortify.FortifyPropertiesCalalogBuilder
import com.manulife.fortify.FortifyResult
import com.manulife.fortify.FortifyRunner
import com.manulife.gating.GatingReport
import com.manulife.git.GitPropertiesCatalogBuilder
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.logger.Logger
import com.manulife.maven.MavenPOMFile
import com.manulife.maven.MavenPropertiesCalalogBuilder
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.snyk.SnykPropertiesCatalogBuilder
import com.manulife.snyk.SnykRunner
import com.manulife.sonarqube.SonarQubePropertiesCalalogBuilder
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.sonarqube.SonarQubeRunner
import com.manulife.sonarqube.SonarQubeUtils
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.Shell
import com.manulife.versioning.IncreasePatchVersion

// TODO:
//  - This is new code which will include complete CIDC and Promotion pipeline.
//  - Uncomment the code once the 6.5 release is completed for testing.
//  - Integration with JIRA to ket JIRA know which version contains what stories/tasks/items - When building a release branch
//  - Integration with Confluence to generate release notes

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        environment {
            SONARQUBE_RUNNER = "\"${tool 'SonarQube Runner'}\\bin\\sonar-runner\""
        }
        tools {
            maven 'Maven 3.3.9'
            jdk "${configuration.latestJava == true ? 'JDK 11' : 'JDK 8u112'}"
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

                        // Read property files.
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
                        batsh = unix ? this.&sh : this.&bat
                        mavenScript = "${tool('Maven 3.3.9')}/bin/mvn" + (unix ? '' : '.cmd')
                        logger.debug("mavenScript: ${mavenScript}")

                        // Maven
                        mvnSettings = "-B -U -s ${WORKSPACE}/settings.xml -f " + pipelineParams.mavenPOMRelativeLocation
                        if (pipelineParams.mavenSettingsFileName != null) {
                            configFileProvider([configFile(fileId: pipelineParams.mavenSettingsFileName, targetLocation: 'settings.xml')]) { }
                        }

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()
                        fortifyTranslated = false

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.AEM_MAVEN, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        logger.debug('EnvironmentVariables: ') {
                            batsh 'env'
                        }
                        logger.debug("BRANCH_NAME=${env.BRANCH_NAME}")

                        mavenPOMFile = new MavenPOMFile(this, mvnSettings)
                        mavenPOMFile.read()
                        projectVersion = mavenPOMFile.getVersion()

                        GitLabUtils.postStatus(this, 'running')
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Resolve Dependencies & Build') {
                environment {
                    AEM_ADMIN_CREDENTIALS = credentials("${pipelineParams.AEMAdminCredentials}")
                    AEM_AUTHOR_CREDENTIALS = credentials("${pipelineParams.AEMAuthorCredentials}")
                    GITLAB_API_TOKEN = credentials("${pipelineParams.gitLabAPITokenName}")
                }
                when { expression { return pipelineParams.buildCommand } }
                steps {
                    script {
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        logger.info('CORE')
                        if (FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName)) {
                            def fortifyBuildId = "aem-${JOB_BASE_NAME}"
                            // Omitting "def" or type to bind the variable globally.
                            fortifyRunner = new FortifyRunner(scriptObj: this, localBranchName: localBranchName,
                                    pipelineParams: pipelineParams, buildId: fortifyBuildId)
                            fortifyRunner.init()
                            if (!pipelineParams.fortifyScanTree && pipelineParams.buildCommand.trim().startsWith('mvn ')) {
                                def fortifyCmd = " -Dfortify.sca.buildId=\"${fortifyBuildId}\"" +
                                        " -Dfortify.sca.sourceanalyzer.executable=\"${fortifyRunner.fortifyRoot}/bin/sourceanalyzer\"" +
                                        ' com.fortify.sca.plugins.maven:sca-maven-plugin:clean' +
                                        ' com.fortify.sca.plugins.maven:sca-maven-plugin:translate'
                                batsh pipelineParams.buildCommand.trim() + fortifyCmd
                                fortifyTranslated = true
                            }
                            else {
                                // Fortify's sourceanalyzer can only use a
                                // maven plugin or spy on msbuild and
                                // xcodebuild builds.  If the build does not
                                // use Maven, we will translate the source tree
                                // at the time of analysis.

                                // Run the build as sourceanalyzer would not do that.
                                batsh "${pipelineParams.buildCommand}"
                            }
                        }
                        else {
                            batsh "${pipelineParams.buildCommand}"
                        }
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Run Unit Tests') {
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        if (PROJECT_ROOT_FOLDER != 'null') {
                            logger.debug("PROJECT_ROOT_FOLDER=${PROJECT_ROOT_FOLDER}")
                            logger.debug("testgoal=${pipelineParams.mavenTestGoal}")
                            dir(PROJECT_ROOT_FOLDER) {
                                batsh "mvn ${mvnSettings} ${pipelineParams.mavenTestGoal}"
                            }
                        }
                        else {
                            batsh "mvn ${mvnSettings} ${pipelineParams.mavenTestGoal}"
                        }
                        stagesExecutionTimeTracker.runUnitTestsStageEnd()
                    }
                }
            }
            stage('Increase Patch Version') {
                when {
                    expression {
                        return Boolean.valueOf(pipelineParams.increasePatchVersion) &&
                               ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) &&
                               ('dev' != pipelineParams.AEMExecuteMode)
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.increasePatchVersionStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        projectVersion = IncreasePatchVersion.perform(this, mavenPOMFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        environment {
                            PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                        }
                        steps {
                                script {
                                    stagesExecutionTimeTracker.codeReviewStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    // Delete an earlier plugin's report-task.txt. Ref: https://jira.sonarsource.com/browse/SONARJNKNS-281
                                    batsh 'rmdir /s /q .scannerwork || exit 0'
                                    String mvnGoal = pipelineParams.mavenBuildGoal
                                    String mvnPom = pipelineParams.mavenPOMRelativeLocation
                                    buildInfo = Artifactory.newBuildInfo()
                                    buildInfo.env.capture = true
                                    logger.debug("mvnGoal=${mvnGoal}")
                                    logger.debug("mvnPom=${mvnPom}")
                                    logger.debug("mvnSettings=${mvnSettings}")
                                    logger.debug("PROJECT_ROOT_FOLDER=${PROJECT_ROOT_FOLDER}")
                                    artifactoryMaven = Artifactory.newMavenBuild()
                                    artifactoryMaven.tool = 'Maven 3.3.9'
                                    artifactoryMaven.resolver releaseRepo: pipelineParams.releaseRepo,
                                            snapshotRepo: pipelineParams.snapshotRepo,
                                            server: artifactoryServer
                                    artifactoryMaven.opts = ' -Dmaven.compiler.fork=true -Dmaven.test.skip'
                                    SonarQubeRunner.runScan(this, PipelineType.AEM_MAVEN, { sonarParams ->
                                                artifactoryMaven.run pom: mvnPom,
                                                        goals: "${mvnSettings} sonar:sonar ${sonarParams}".toString(),
                                                        buildInfo: buildInfo
                                        },
                                        unix, MRCommitsList, projectVersion)
                                    SonarQubeRunner.checkScan(this, sonarQubeResult)
                                    stagesExecutionTimeTracker.codeReviewStageEnd()
                                }
                        }
                    }
                    stage ('Open-Source Governance') {
                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                        environment {
                            BLACKDUCK = credentials("${pipelineParams.hubUserPasswordTokenName}")
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.AEM_MAVEN)
                                logger.debug("mvnSettings=${mvnSettings}")
                                logger.debug("pipelineParams.hubExcludedModules=${pipelineParams.hubExcludedModules}")
                                logger.debug("mavenScript=${mavenScript}")
                                blackDuckResult = blackDuckRunner.callBlackDuck(
                                    """
                                        "--detect.maven.build.command=${mvnSettings.trim()}"
                                        "--detect.maven.path=${mavenScript}"
                                        "--detect.maven.excluded.modules=${pipelineParams.hubExcludedModules}"
                                        "--detect.maven.scope=runtime"
                                    """)
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

                                snykRunner.call('')

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
                        when { expression { return FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName) } }
                        environment {
                            FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.securityCodeScanningStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                fortifyResult = fortifyRunner.run(fortifyTranslated ? null : (pipelineParams.fortifyScanTree ?: '.'))
                                if (!fortifyResult.codeSecurityGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.fortifyGating)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Code Security assessment: ${fortifyResult.message}")
                                    }
                                    else if (currentBuild.result != 'FAILED') {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }
                                stagesExecutionTimeTracker.securityCodeScanningStageEnd()
                            }
                        }
                    }
                }
            }
            stage('Package and Store') {
                when {
                    expression {
                        return (localBranchName != null && localBranchName.matches('(develop).*')) &&
                                pipelineParams.artifactoryDeploymentPattern != null &&
                                ('MERGE' != env.gitlabActionType &&
                                'NOTE' != env.gitlabActionType)
                    }
                }
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug("projectRootFolder: ${pipelineParams.projectRootFolder}")
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        // Check that there isn't already an artifact with that commit id in Artifactory
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT, pipelineParams)
                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                        }
                        else {
                            artifactoryHelper.uploadMavenArtifact(pipelineParams,
                                                                GIT_COMMIT,
                                                                mvnSettings,
                                                                sonarQubeResult.codeQualityGatePassed,
                                                                blackDuckResult.governanceGatePassed,
                                                                snykRunner.result.governanceGatePassed,
                                                                fortifyResult.codeSecurityGatePassed,
                                                                sonarQubeResult.message,
                                                                blackDuckResult.message,
                                                                snykRunner.result.message,
                                                                fortifyResult.message,
                                                                projectVersion.toString())
                        }
                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
                    }
                }
            }
            stage('Setup Front End Components') {
                when { 
                    expression { return pipelineParams.appFEComponentGitLocation &&
                     ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)}
                }
                steps {
                    dir('frontend') {
                    git branch: "${pipelineParams.appFEComponentGitBranch}",
                        credentialsId: "${pipelineParams.gitJenkinsSSHCredentials}",
                        url: "${pipelineParams.appFEComponentGitLocation}"
                    }
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def syncCommand = 'rsync -art '
                        def appFEScript = "\"${pipelineParams.appFEScriptsSource}\" \"${pipelineParams.appFEScriptsTarget}\""
                        def appFEStyles = "\"${pipelineParams.appFEStylesSource}\" \"${pipelineParams.appFEStylesTarget}\""
                        batsh syncCommand + appFEScript
                        batsh syncCommand + appFEStyles
                    }
                }
            }
            stage('Deploy Code') {
                environment {
                    AEM_ADMIN_CREDENTIALS = credentials("${pipelineParams.AEMAdminCredentials}")
                    AEM_AUTHOR_CREDENTIALS = credentials("${pipelineParams.AEMAuthorCredentials}")
                    GITLAB_API_TOKEN = credentials("${pipelineParams.gitLabAPITokenName}")
                    AEM_PUBLISHER_CREDENTIALS = credentials("${pipelineParams.AEMPublisherCredentials}")
                    AEM_PUBLISHER2_CREDENTIALS = credentials("${pipelineParams.AEMPublisher2Credentials}")
                    FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
                }
                when { 
                    expression { return ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)}
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('Started deploying the code.')
                        if (pipelineParams.pubDeployCommand != null) {
                            logger.info('Deploy Core Started')
                            batsh "${pipelineParams.pubDeployCommand}"
                            logger.info('Deploy Core Ended')
                        }
                        if (pipelineParams.authDeployCommand != null) {
                            batsh "${pipelineParams.authDeployCommand}"
                            logger.info('Deploy UI Started')
                            batsh "${pipelineParams.authDeployCommand}"
                            logger.info('Deploy UI Ended')
                        }
                        if (pipelineParams.uiDeploy2Command != null) {
                            //Used for secondary deployment to node 2 in staging
                            batsh "${pipelineParams.uiDeploy2Command}"
                        }
                        //Below code is for dispatcher code deployment.
                        if (pipelineParams.dispatcherScriptPath != null) {
                            logger.info('Start Dispatcher Script Execution')
                            if (pipelineParams.AEMDispatcherIPAddress1 != null) {
                                logger.info("Execute Dispatcher Script for ip: ${pipelineParams.AEMDispatcherIPAddress1} and Mode: ${pipelineParams.AEMExecuteMode}")
                                batsh "chmod +x ${pipelineParams.dispatcherScriptPath} && " +
                                     "./${pipelineParams.dispatcherScriptPath} ${env.BUILD_NUMBER} ${pipelineParams.AEMDispatcherIPAddress1} ${pipelineParams.AEMExecuteMode}"
                            }
                            if (pipelineParams.AEMDispatcherIPAddress2 != null) {
                                logger.info("Execute Dispatcher Script for ip: ${pipelineParams.AEMDispatcherIPAddress2} and Mode: ${pipelineParams.AEMExecuteMode}")
                                batsh "chmod +x ${pipelineParams.dispatcherScriptPath} && " +
                                     "./${pipelineParams.dispatcherScriptPath} ${env.BUILD_NUMBER} ${pipelineParams.AEMDispatcherIPAddress2} ${pipelineParams.AEMExecuteMode}"
                            }
                            logger.info('End Dispatcher Script Execution')
                        }
                        logger.info('Completed deploying the code.')
                    }
                }
            }
            stage('Dispatcher Clear Cache') {
                when { expression { return pipelineParams.deploymentJenkinsJobName  &&
                ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)
                    }
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.info('Dispatcher Clear Cache Job Called!')
                        build job: "${pipelineParams.deploymentJenkinsJobName}",
                            wait: false,
                            parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                    }
                }
            }
            stage('Package and Store non-Snapshot') {
                when { 
                    expression { return ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)}
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        configFileProvider([configFile(fileId: 'settings.xml', variable : 'Maven_settings')]) {
                                batsh 'mvn versions:set -DremoveSnapshot'
                        }
                        batsh "${pipelineParams.buildCommand}"
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        // Check that there isn't already an artifact with that commit id in Artifactory
                        artifactoryHelper.uploadMavenArtifact(pipelineParams,
                                                            GIT_COMMIT,
                                                            mvnSettings,
                                                            sonarQubeResult.codeQualityGatePassed,
                                                            blackDuckResult.governanceGatePassed,
                                                            snykRunner.result.governanceGatePassed,
                                                            fortifyResult.codeSecurityGatePassed,
                                                            sonarQubeResult.message,
                                                            blackDuckResult.message,
                                                            snykRunner.result.message,
                                                            fortifyResult.message,
                                                            projectVersion.toString())
                    }
                }
            }
        }
        post {
            always {
                archiveArtifacts artifacts: '**/target/*.zip' , allowEmptyArchive: true
                archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true
                script {
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()
                }
                cleanWs()
            }
        }
        parameters {
            booleanParam(
                name: 'forceFullScan',
                defaultValue: false,
                description: 'Used to force the calls to BlackDuck and Fortify for development branch'
            )
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
                noteRegex: '.*[Jj]enkins.*[Bb]uild.*',
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
    propertiesCatalog.addOptionalProperty('projectName', 'Defaulting projectName property to null', null)
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName to null. ' +
                                                                         'Could be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this pipelines.', null)
    propertiesCatalog.addOptionalProperty('appFEComponentGitLocation', 'Defaulting appFEComponentGitLocation property to null', null)
    propertiesCatalog.addOptionalProperty('appFEComponentGitBranch', 'Defaulting appFEComponentGitBranch property to master', 'master')
    propertiesCatalog.addOptionalProperty('appFEScriptsSource', 'Defaulting appFEScriptsSource property to null', null)
    propertiesCatalog.addOptionalProperty('appFEScriptsTarget', 'Defaulting appFEScriptsTarget property to null', null)
    propertiesCatalog.addOptionalProperty('appFEStylesSource', 'Defaulting appFEStylesSource property to null', null)
    propertiesCatalog.addOptionalProperty('appFEStylesTarget', 'Defaulting appFEStylesTarget property to null', null)
    propertiesCatalog.addMandatoryProperty('AEMAdminCredentials', '[ERROR]: Missing AEMAdminCredentials property value from Jenkins Credentials')
    propertiesCatalog.addMandatoryProperty('AEMAuthorCredentials', '[ERROR]: Missing AEMAuthorCredentials property value from Jenkins Credentials')
    propertiesCatalog.addMandatoryProperty('AEMPublisherCredentials', '[ERROR]: Missing AEMPublisherCredentials property value from Jenkins Credentials')
    propertiesCatalog.addOptionalProperty('AEMPublisher2Credentials', 'Defaulting AEMPublisher2Credentials property to null', 'AEM-STAGE-Publisher2-6.3')
    propertiesCatalog.addMandatoryProperty('buildCommand', '[ERROR]: Missing buildCommands build commands')
    propertiesCatalog.addOptionalProperty('pubDeployCommand', 'Defaulting pubDeployCommand property to null.', null)
    propertiesCatalog.addOptionalProperty('authDeployCommand', 'Defaulting authDeployCommand property to null.', null)
    propertiesCatalog.addOptionalProperty('uiDeploy2Command', 'Defaulting uiDeploy2Command property to null', null)
    propertiesCatalog.addOptionalProperty('dispatcherScriptPath', 'Defaulting dispatcherScriptPath property to null', null)
    propertiesCatalog.addOptionalProperty('AEMDispatcherIPAddress1', 'Defaulting AEMDispatcherIPAddress1 property to null', null)
    propertiesCatalog.addOptionalProperty('AEMDispatcherIPAddress2', 'Defaulting AEMDispatcherIPAddress2 property to null', null)
    propertiesCatalog.addOptionalProperty('AEMExecuteMode', 'Defaulting AEMExecuteMode property to null. This property holds the mode in which job is running. ' +
                                                             'Like running on Dev, QA or Stage Modes.', null)
    propertiesCatalog.addOptionalProperty('groupId', 'Defaulting groupId property to null. This property holds the sub-folder where the artifacts are stored.', null)
    propertiesCatalog.addOptionalProperty('artifactoryDeploymentPattern', 'Defaulting artifactoryDeploymentPattern property to null', null)
    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    MavenPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)
    return propertiesCatalog
}
