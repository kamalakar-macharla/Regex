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

// TODO:
//   - Split into Continuous Integration and Continuous Delivery pipelines.  Currently doing both
//   - Store package on Artifactory + add properties to artifact.  Deployment pipeline should take package from Artifactory.

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        environment {
            XCODEVER = sh(script: 'xcode-select --print-path', returnStdout: true)
            PATH  = "/usr/local/bin:$PATH"
            //TODO - Determine correct path and commands for MAC - create global variable. Look at node variable to override values
            SONARQUBE_RUNNER = '//Users//dsmobile-imac//sonar-runner//bin//sonar-runner'
        }
        tools {
            jdk 'JDK 8u112'
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

                        // "Using def or an explicit type like in the example below would fail because you would then create a local variable"
                        // http://groovy-lang.org/integrating.html#_sharing_data_between_a_script_and_the_application
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

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()
                        fortifyRequested = FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName)
                        // Code to be used once we figure how to scan Swift projects with Fortify.
                        //  = FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName)

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.SWIFT, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        // TODO: Figure out how to deal with projectVersion
                        projectVersion = null
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage ('Environment Setup') {
                steps {
                    sh "${pipelineParams.xcodeVersionSelectCommand}"
                }
            }
            stage ('Resolve Dependencies') {
                when { expression { return pipelineParams.cocoapodsDependenciesCommand } }
                steps {
                    sh "${pipelineParams.cocoapodsDependenciesCommand}"
                }
            }
            stage('Run Unit Tests') {
                when { expression { return pipelineParams.xcodeWorkSpace && pipelineParams.xcodeScheme && pipelineParams.xcodeTestSimulator  } }
                environment {
                    FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        def buildcmd = "xcodebuild clean test -workspace ${pipelineParams.xcodeWorkSpace} -scheme ${pipelineParams.xcodeScheme} -destination ${pipelineParams.xcodeTestSimulator}"
                        if (fortifyRequested) {
                            def fortifyBuildId = "swift-${JOB_BASE_NAME}"
                            fortifyRunner = new FortifyRunner(scriptObj: this,
                                                              localBranchName: localBranchName,
                                                              pipelineParams: pipelineParams,
                                                              buildId: fortifyBuildId,
                                                              opts: '-p MOBILE')
                            fortifyRunner.init()
                            if (pipelineParams.fortifyScanTree) {
                                sh buildcmd
                            }
                            else {
                                fortifyRunner.translateOnly(buildcmd)
                            }
                        }
                        else {
                            sh buildcmd
                        }
                        stagesExecutionTimeTracker.runUnitTestsStageEnd()
                    }
                }
            }
            stage('Quality and Security Scanning') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        steps {
                            script {
                                stagesExecutionTimeTracker.codeReviewStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                SonarQubeRunner.runnerScan(this, PipelineType.SWIFT, unix, MRCommitsList, sonarQubeResult, projectVersion)
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
                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.SWIFT)
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
                        when { expression { return fortifyRequested } }
                        environment {
                            FORTIFY_MANAGE_APPLICATION_TOKEN = credentials("${pipelineParams.fortifyTokenName}")
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.securityCodeScanningStageStart()
                                FAILED_STAGE = env.STAGE_NAME
                                fortifyResult = fortifyRunner.run(pipelineParams.fortifyScanTree)
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
            stage('Apply Environment Configuration') {
                when {
                    expression {
                        // TODO:  Why do we need to validate the same thing 3 times?????????
                        return pipelineParams.appEnvConfigGitLocation &&
                               pipelineParams.appEnvConfigGitLocation &&
                               pipelineParams.appEnvConfigGitLocation
                    }
                }
                steps {
                    sh "git clone ${pipelineParams.appEnvConfigGitLocation}"
                    sh "cp -f ${pipelineParams.appEnvConfigName} ${pipelineParams.appEnvConfigMoveTo}"
                }
            }
            stage('Archiving & Signing') {
                when {
                    expression {
                        return pipelineParams.xcodePath &&
                               pipelineParams.xcodeExportPlist &&
                               pipelineParams.xcodeScheme
                    }
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Archiving...')
                        def branch = "${env.GIT_BRANCH}".split('/')[1]
                        sh "xcodebuild clean archive -configuration ${branch} -workspace " +
                             "${pipelineParams.xcodeWorkSpace} -scheme ${pipelineParams.xcodeScheme} -archivePath " +
                             "${pipelineParams.xcodePath}-${BUILD_NUMBER}.xcarchive -quiet"
                        logger.debug('Signing...')
                        sh "xcodebuild -exportArchive -archivePath ${pipelineParams.xcodePath}-${BUILD_NUMBER}.xcarchive " +
                             "-exportOptionsPlist ${pipelineParams.xcodeExportPlist} -exportPath " +
                             "${pipelineParams.xcodePath}-${BUILD_NUMBER} -quiet"
                    }
                }
            }
            stage('Create Release Notes') {
                when { expression { return pipelineParams.releaseNotesCommand } }
                steps {
                    sh "${pipelineParams.releaseNotesCommand}"
                }
            }
            stage('Commit Build Version') {
                when { expression { return pipelineParams.xcodePlistFiles && ('MERGE' != env.gitlabActionType) } }
                steps {
                    sh "agvtool new-version -all ${BUILD_NUMBER}"
                    sh "git add ${pipelineParams.xcodePlistFiles}"
                    sh 'git commit -m \"Increment build version to \$(agvtool what-version | sed -n 2p) [ci-skip]\"'
                    sh "git push origin HEAD:${localBranchName} || :"
                }
            }
            stage('Deploy to HockeyApp') {
                when { expression { return ! (localBranchName ==~ 'feature/.*') } }
                steps {
                    step([
                        $class: 'HockeyappRecorder',
                        applications:
                            [
                                [
                                    apiToken: "${pipelineParams.hockeyAppAPIToken}",
                                    downloadAllowed: true,
                                    filePath: "${pipelineParams.hockeyAppFilePath}",
                                    mandatory: false,
                                    notifyTeam: false,
                                    releaseNotesMethod: [
                                        $class: "${pipelineParams.hockeyReleaseNotesClass}",
                                        fileName: "${pipelineParams.hockeyReleaseNotesFileName}",
                                        isMarkdown: true
                                    ],
                                    uploadMethod: [
                                        $class: 'VersionCreation',
                                        appId: "${pipelineParams.hockeyAppAppId}"
                                    ]
                                ]
                            ],
                        debugMode: false,
                        failGracefully: false
                    ])
                }
            }
        }
        post {
            always {
                //TODO STASH TO RETRIEVE BUILD IN ANOTHER JOB
                archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true
                archiveArtifacts artifacts: '**/*.ipa', allowEmptyArchive: false

                sh 'sudo xcode-select --switch $XCODEVER'

                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()
                }
                cleanWs()
            }
            success {
                updateGitlabCommitStatus name: 'build', state: 'success'
            }
            failure {
                updateGitlabCommitStatus name: 'build', state: 'failed'
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

    propertiesCatalog.addMandatoryProperty('xcodeVersionSelectCommand',
                                           '[ERROR]: Missing xcodeVersionSelectCommand property value.  Must be the version of xcode you wish to use to build the mobile application')

    propertiesCatalog.addOptionalProperty('appEnvConfigGitLocation', 'Defaulting appEnvConfigGitLocation property to null.  Could be set to define git location were configuration is stored', null)
    propertiesCatalog.addOptionalProperty('appEnvConfigMoveTo', 'Defaulting appEnvConfigMoveTo property to null.  Could be set to define the project location to copy the configuration to', null)
    propertiesCatalog.addOptionalProperty('appEnvConfigName', 'Defaulting appEnvConfigName property to null.  Could be set to define the configuration environment', null)
    propertiesCatalog.addOptionalProperty('cocoapodsDependenciesCommand', 'Defaulting cocoapodsDependenciesCommand property to null.  Could be set to install cocoapod dependencies', null)
    propertiesCatalog.addOptionalProperty('projectName', 'Defaulting projectName property to null', null)
    propertiesCatalog.addOptionalProperty('releaseNotesCommand', 'Defaulting releaseNotesCommand property to null.  Could be set to generate a release notes file', null)
    propertiesCatalog.addOptionalProperty('xcodeTestSimulator', 'Defaulting xcodeTestSimulator property to null.  Could be set to define the unit test simulator type', null)
    propertiesCatalog.addOptionalProperty('xcodeWorkSpace', 'Defaulting xcodeWorkSpace property to null.  Could be set to define the xcode workspace', null)
    propertiesCatalog.addOptionalProperty('xcodeScheme', 'Defaulting xcodeScheme property to null.  Could be set to define the xcode project name', null)
    propertiesCatalog.addOptionalProperty('xcodePath',
                                          'Defaulting xcodePath property to null.  Could be set to define the path of the xcarchive that will combine the build number eg Bank-BUILD#.xcarchive',
                                          null)
    propertiesCatalog.addOptionalProperty('xcodeExportPlist', 'Defaulting xcodeExportPlist property to null.  Could be set to define the xcode export plist for signing', null)
    propertiesCatalog.addOptionalProperty('xcodePlistFiles', 'Defaulting xcodePlistFiles property to null.  Could be set to define the build # commit to source code with PList files', null)

    // TODO: will move to deploy script at some point. Needs to be manditory as well once moved
    propertiesCatalog.addOptionalProperty('hockeyAppAPIToken', 'Defaulting hockeyAppAPIToken property to null.  Could be set to define the hockeyApp API Token', null)
    propertiesCatalog.addOptionalProperty('hockeyAppAppId', 'Defaulting hockeyAppAppId property to null.  Could be set to define the hockeyApp AppId for upload', null)
    propertiesCatalog.addOptionalProperty('hockeyAppFilePath', 'Defaulting hockeyAppFilePath property to null.  Could be set to define the sign ipa location to upload', null)
    propertiesCatalog.addOptionalProperty('hockeyReleaseNotesClass', 'Defaulting hockeyReleaseNotesClass property to \'NoReleaseNotes\'.', 'NoReleaseNotes')
    propertiesCatalog.addOptionalProperty('hockeyReleaseNotesFileName', 'Defaulting hockeyReleaseNotesFileName property to null. Must be set if hockeyReleaseNotesClass is \'FileReleaseNotes\'', null)
    // End of TODO

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)

    return propertiesCatalog
}