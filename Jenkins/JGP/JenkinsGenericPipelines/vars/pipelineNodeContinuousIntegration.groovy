import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
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
import com.manulife.nodejs.PackageJsonFile
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
import com.manulife.util.Strings
import com.manulife.versioning.IncreasePatchVersion

// TODO:
//  - Upload to Artifactory using the Jenkins plugin so that we have the commit_id on the artifact

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        tools {
            jdk 'JDK 8u112'
        }
        // The environment and tools sections will be replaced by the Docker container configuration when we move to the new Jenkins servers.
        /*
        environment {
            // Work around the system configuration at jenkins.manulife.com
            // propagating JAVA_HOME with C:\Program Files\Java\jdk1.8.0_211 to
            // all agents when these agents are configured to use the
            // respective selection in the "tool location" drop-down list.  The
            // unexpected Windows notation causes, in its turn, /usr/bin/java
            // to use the latest version such as
            // /Library/Java/JavaVirtualMachines/jdk-10.0.2.jdk instead of one
            // selected by a disk-based configuration such as
            // ~/Library/Preferences/java_home.plist,
            //
            //      https://gist.github.com/hogmoru/8e02cf826c840914a8ed93fd418ed88e
            //
            //      https://issues.jenkins-ci.org/browse/JENKINS-58131
            //
            JAVA_HOME = ''
            // The above tools jdk 'JDK 8u112' clause sets own JAVA_HOME for
            // the agent, relieving it from using the disk-based configuration.
            // Therefore this work-around is commented out.
        }
        */
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

                        if (pipelineParams.nodePackageCommand) {
                            if (pipelineParams.nodePackageCommand == 'npm pack' && !pipelineParams.releaseRepo) {
                                 error('When running \'npm pack\' for the Node packaging property (nodePackageCommand) it is required that the releaseRepo property is provided')
                            }
                        }

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)
                        batsh = unix ? this.&sh : this.&bat

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        logger.debug("Artifactory URL: ${artifactoryServer.url}")

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.NODEJS, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        // TODO: Need to cleanup the use of increasePatchVersion and increaseVersion but this would be a breaking change for end users
                        increaseVersion = pipelineParams.increaseVersion ?: (Boolean.valueOf(pipelineParams.increasePatchVersion) ? 'patch' : null)

                        // TODO: Review with Tyler.  Not sure if the notion of pre-release is still relevant anymore (handled by SemVersion class)
                        // increaseVersion = Boolean.valueOf(pipelineParams.increaseBetaRelease) ? 'prerelease --preid=beta' : increaseVersion

                        packagesJsonFile = new PackageJsonFile(this)
                        packagesJsonFile.read()
                        projectVersion = packagesJsonFile.getVersion()

                        GitLabUtils.postStatus(this, 'running')

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Node project version') {
                when { expression { return increaseVersion != null && !(env.gitlabActionType in ['MERGE', 'NOTE']) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.increasePatchVersionStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        projectVersion = IncreasePatchVersion.perform(this, packagesJsonFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage ('Resolve Dependencies & Build') {
                when { expression { return pipelineParams.nodeBuildCommand } }
                steps {
                    script {
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        batsh "${pipelineParams.nodeBuildCommand}"
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Run Unit Tests') {
                when { expression { return pipelineParams.nodeUnitTestCommand } }
                steps {
                    script {
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        if (pipelineParams.unitTestSecret) {
                            withCredentials([string(credentialsId: "${pipelineParams.unitTestSecret}", variable: 'UNIT_TEST_SECRET')]) {
                                writeFile(file: 'token.txt', encoding: 'UTF-8', text: UNIT_TEST_SECRET)
                            }
                        }
                        batsh "${pipelineParams.nodeUnitTestCommand}"
                        stagesExecutionTimeTracker.runUnitTestsStageEnd()
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        steps {
                            script {
                                stagesExecutionTimeTracker.codeReviewStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                SonarQubeRunner.runnerScan(this, PipelineType.NODEJS, unix, MRCommitsList, sonarQubeResult, projectVersion)
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

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.NODEJS)
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

                                snykRunner.call('')

                                if (!snykRunner.result.governanceGatePassed) {
                                    if (Boolean.valueOf(pipelineParams.snykGatingEnabled)) {
                                        currentBuild.result = 'FAILED'
                                        error("Failed on Open-Source Governance assessment: ${snykRunner.result.message}")
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

                                def fortifyBuildId = "nodejs-${JOB_BASE_NAME}"
                                fortifyRunner = new FortifyRunner(scriptObj: this,
                                                                  localBranchName: localBranchName,
                                                                  pipelineParams: pipelineParams,
                                                                  buildId: fortifyBuildId)
                                fortifyRunner.init()
                                fortifyResult = fortifyRunner.run(pipelineParams.fortifyScanTree ?: '.')
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
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*')) &&
                               pipelineParams.nodePackageCommand != null &&
                               ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        def extension = '*.tgz'
                        def moduleVersion = sh(returnStdout: true, script: '''node -p "require('./package.json').version"''').trim()
                        def moduleName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()

                        // Check if already exist
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                               extension,
                                                                               pipelineParams.releaseRepo)
                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            return
                        }

                        String npmign = null
                        if (fileExists('.npmignore')) {
                            npmign = Strings.deBOM(readFile(file: '.npmignore', encoding: 'UTF-8'))
                        }
                        String gitign = null
                        if (fileExists('.gitignore')) {
                            gitign = Strings.deBOM(readFile(file: '.gitignore', encoding: 'UTF-8'))
                        }
                        npmignadj = """
# The .gitignore contents
${gitign ?: ''}

# Heuristic exclusions of files not expected to be published
artifact.*
build
artifact
blackduck*
package
settings.xml
nuget.config
.npmrc
fortify*
*.pdf
.sonar
.sonarqube
sonar-zscaler*
sonaqube-status.json
.nyc_output

# Request to always publish some directories such as dist and lib by putting an exclamation mark.
!dist
!lib

# The above can be overriden with the .npmignore contents that is included last here.
${npmign ?: ''}
"""
                        logger.debug("Adjusting .npmignore as follows: ==============${npmignadj}================")
                        writeFile(file: '.npmignore', encoding: 'UTF-8', text: npmignadj)
                        batsh pipelineParams.nodePackageCommand
                        logger.debug('Folder Content:') { batsh 'ls -la' }
                        if (npmign != null) {
                            writeFile(file: '.npmignore', encoding: 'UTF-8', text: npmign)
                        }

                        if (pipelineParams.projectType == 'NodeJS') {
                            if (fileExists("${moduleName}-${moduleVersion}.tgz")) {
                                artifactoryHelper.uploadArtifact(pipelineParams,
                                                                "${moduleName}-${moduleVersion}.tgz",
                                                                "${pipelineParams.releaseRepo}/${moduleName}/${moduleName}-${moduleVersion}.tgz",
                                                                sonarQubeResult.codeQualityGatePassed,
                                                                blackDuckResult.governanceGatePassed,
                                                                snykRunner.result.governanceGatePassed,
                                                                fortifyResult.codeSecurityGatePassed,
                                                                sonarQubeResult.message,
                                                                blackDuckResult.message,
                                                                snykRunner.result.message,
                                                                fortifyResult.message,
                                                                moduleVersion)

                                if (Boolean.valueOf(pipelineParams.increaseBetaRelease)) {
                                    batsh """
                                        npm dist-tag add ${moduleName}@${moduleVersion} \
next --registry=${artifactoryServer.url}/api/npm/${pipelineParams.releaseRepo}/
                                        npm dist-tag ls \
${moduleName} --registry=${artifactoryServer.url}/api/npm/${pipelineParams.releaseRepo}/
                                        """
                                }
                            }
                            else {
                                logger.warning( """
Skipping the upload due to missing the npm pack result, \"${moduleName}-${moduleVersion}.tgz\".
It is probably already published because of the property,
    nodePackageCommand: ${pipelineParams.nodePackageCommand}
""")
                            }
                        }

                        if (pipelineParams.projectType == 'Static') {
                            batsh """
                            mv build package || true
                            mv Staticfile package/ || true
                            cp -r nginx package/ || true
                            cp -r manifest-*.yml package/ || true
                            tar -cvzf ${moduleName}-${moduleVersion}.tgz package"""

                            if (fileExists("${moduleName}-${moduleVersion}.tgz")) {
                                artifactoryHelper.uploadArtifact(pipelineParams,
                                                                "${moduleName}-${moduleVersion}.tgz",
                                                                "${pipelineParams.releaseRepo}/${moduleName}/${moduleName}-${moduleVersion}.tgz",
                                                                sonarQubeResult.codeQualityGatePassed,
                                                                blackDuckResult.governanceGatePassed,
                                                                snykRunner.result.governanceGatePassed,
                                                                fortifyResult.codeSecurityGatePassed,
                                                                sonarQubeResult.message,
                                                                blackDuckResult.message,
                                                                snykRunner.result.message,
                                                                fortifyResult.message,
                                                                moduleVersion)
                            }
                            else {
                                    logger.warning( """
Skipping the upload due to missing the npm pack result, \"${moduleName}-${moduleVersion}.tgz\".
It is probably already published because of the property,
    nodePackageCommand: ${pipelineParams.nodePackageCommand}
""")
                            }
                        }

                        //Old logic for ReactJS
                        //TO-DO REFINE LOGIC
                        if (pipelineParams.projectType == 'ReactJS') {
                            sh '''
                            mkdir -p artifact/nginx/conf/includes
                            # Move build app folder to artifact folder
                            mv build artifact/ || true
                            # Move Staticfile file to artifact folder (if exists)
                            mv Staticfile artifact/ || true
                            # Move Custom nginx config file to artifact folder (if exist)
                            mv *-nginx.conf artifact/nginx/conf/includes/ || true
                            # Move PCF manifesto artifact folder (if exist)
                            mv manifest-*.yml artifact/'''

                            //if not called by gitlab plug-in use job base name as default
                            if (!binding.hasVariable('gitlabSourceRepoName')) {
                                    gitlabSourceRepoName = env.JOB_BASE_NAME
                            }

                            // Create an tar archive (It would be better in zip because PCF can only handle
                            if (fileExists('artifact')) {
                                // zip or folder (not tarball) But we don't have `zip` binary installed on Jenkins for now)
                                sh "tar -czf ${gitlabSourceRepoName}.tar.gz artifact/"

                                // Upload Artifactory
                                def version = sh(returnStdout: true, script: '''node -p "require('./package.json').version"''').trim()
                                artifactoryHelper.uploadArtifact(pipelineParams,
                                                                "${gitlabSourceRepoName}.tar.gz",
                                                                "${pipelineParams.releaseRepo}/${gitlabSourceRepoName}/${gitlabSourceRepoName}-${version}.${BUILD_ID}.tar.gz",
                                                                sonarQubeResult.codeQualityGatePassed,
                                                                blackDuckResult.governanceGatePassed,
                                                                snykRunner.result.governanceGatePassed,
                                                                fortifyResult.codeSecurityGatePassed,
                                                                sonarQubeResult.message,
                                                                blackDuckResult.message,
                                                                snykRunner.result.message,
                                                                fortifyResult.message,
                                                                moduleVersion)
                            }
                            else {
                                logger.warning('Skipping the upload because directory \"artifact\" did not exist')
                                batsh 'ls -la'
                            }
                        }

                        stagesExecutionTimeTracker.packageAndStoreStageEnd()
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
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
                archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true
                script {
                    PipelineRunAuditTrailing.log(this)
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

    propertiesCatalog.addOptionalProperty('projectType', 'Defaulting projectType to NodeJS.  Could also be set to ReactJS', 'NodeJS')
    propertiesCatalog.addOptionalProperty('nodeBuildCommand', 'Defaulting nodeBuildCommand property to \"npm install\"', 'npm install')
    propertiesCatalog.addOptionalProperty('nodeUnitTestCommand', 'Defaulting nodeUnitTestCommand property to null.', null)
    propertiesCatalog.addOptionalProperty('nodePackageCommand', 'Defaulting nodePackageCommand property to null.', null)
    //TEMP Solution to make it optional but should be made manitory and added to ArtifactoryPropertiesCalalogBuilder. Making it manitory should
    propertiesCatalog.addOptionalProperty('releaseRepo', 'Defaulting releaseRepo property to null. Should be set to the Artifactory release repo name. An example would be example-npm', null)
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)
    propertiesCatalog.addOptionalProperty('unitTestSecret', 'Defaulting unitTestSecret property to null.', null)

    propertiesCatalog.addOptionalProperty('increaseVersion', 'Defaulting increaseVersion to null.  Setting it to major, minor, patch increments the respective part of the version.', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')
    propertiesCatalog.addOptionalProperty('increaseBetaRelease',
                                          'Defaulting increaseBetaRelease to null.  Setting it to true will create a beta version tag. ' +
                                            'If increaseVersion was also used it will be overwritten by this value',
                                          null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)

    return propertiesCatalog
}
