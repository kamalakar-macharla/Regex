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
import com.manulife.pipeline.PipelineType
import com.manulife.python.ParametersJsonFile
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

                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }
                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)
                        batsh = unix ? this.&sh : this.&bat

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.PYTHON, localBranchName)

                        // SonarQube result
                        sonarQubeResult = new SonarQubeResult()
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        parametersJsonFile = new ParametersJsonFile(this)
                        parametersJsonFile.read()
                        projectVersion = parametersJsonFile.getVersion()

                        GitLabUtils.postStatus(this, 'running')

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Increase Patch Version') {
             when { expression { return Boolean.valueOf(pipelineParams.increasePatchVersion) && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
                steps {
                    script {
                        stagesExecutionTimeTracker.increasePatchVersionStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        projectVersion = IncreasePatchVersion.perform(this, parametersJsonFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage('Resolve Dependencies & Build') {
                when { expression { return pipelineParams.pythonBuildCommand } }
                steps {
                    script {

                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        srcFolder = parametersJsonFile.src

                        if (pipelineParams.pyVersion == 'py3') {
                            batsh """
                            virtualenv testvenv --system-site-packages -p `which python${pipelineParams.pyLangVersion}`
                            source testvenv/bin/activate
                            ${pipelineParams.pythonBuildCommand}
                            """
                        }
                        else {
                                batsh """
                                virtualenv testvenv --system-site-packages -p `which python2.7`
                                source testvenv/bin/activate
                                ${pipelineParams.pythonBuildCommand}
                                    """
                        }
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }

                }
            }
            stage('Run Unit Test') {
                when { expression { return pipelineParams.pythonUnitTestCommand } }
                steps {
                    script {
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        FAILED_STAGE = env.STAGE_NAME
                        srcFolder = parametersJsonFile.src
                        sh "cp -R ${srcFolder} testvenv/"
                        sh 'cp setup.py parameters.json testvenv/ '
                        dir('testvenv') {
                            batsh """
                            source bin/activate
                            pip list
                            ${pipelineParams.pythonUnitTestCommand}
                            """
                        }
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
                                SonarQubeRunner.runnerScan(this, PipelineType.PYTHON, unix, MRCommitsList, sonarQubeResult, projectVersion)
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

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.PYTHON)
                                blackDuckResult = blackDuckRunner.callBlackDuck('--detect.python.python3=true --detect.python.path=testvenv/bin/python3')
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

                                def fortifyBuildId = "python-${JOB_BASE_NAME}"
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
                               pipelineParams.pythonPackageCommand != null &&
                               ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType)
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        def extension = '*.whl'
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

                        batsh "${pipelineParams.pythonPackageCommand}"

                        final PKG_NAME = parametersJsonFile.name
                        final PKG_VERSION = projectVersion.toString()

                        // Merge process of making a single tar file for two artifacts (Generic Artifactory Management)
                        dir('dist') {
                            sh 'mkdir latest'
                            sh 'mv *.whl *.tar.gz latest'
                            sh "tar cvf ${PKG_NAME}-${PKG_VERSION}.tar.gz latest"
                        }
                        artifactoryHelper.uploadArtifact(pipelineParams,
                                                        "dist/${PKG_NAME}-${PKG_VERSION}.tar.gz",
                                                        "${pipelineParams.releaseRepo}/${PKG_NAME}/v${PKG_VERSION}/",
                                                        sonarQubeResult.codeQualityGatePassed,
                                                        blackDuckResult.governanceGatePassed,
                                                        snykRunner.result.governanceGatePassed,
                                                        fortifyResult.codeSecurityGatePassed,
                                                        sonarQubeResult.message,
                                                        blackDuckResult.message,
                                                        snykRunner.result.message,
                                                        fortifyResult.message,
                                                        PKG_VERSION)

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
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
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

    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName',
                                          'Defaulting deploymentJenkinsJobName to null.  ' +
                                            'Could be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this pipelines.',
                                          null)
    propertiesCatalog.addOptionalProperty('pythonBuildCommand', 'Defaulting pythonBuildCommand property to \"pip\"', 'pip')
    propertiesCatalog.addOptionalProperty('pythonPackageCommand', 'Defaulting pythonPackageCommand property to \"null\"', null)
    propertiesCatalog.addOptionalProperty('pythonUnitTestCommand', 'Defaulting pythonUnitTestCommand property to null', null)
    propertiesCatalog.addOptionalProperty('pyLangVersion', 'Defaulting pyLangVersion property to \"3.6\"', '3.6')
    propertiesCatalog.addOptionalProperty('pyVersion', 'Defaulting pyVersion property to \"py3\"', 'py3')


    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)

    return propertiesCatalog
}
