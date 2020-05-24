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
import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.microsoft.MicrosoftException
import com.manulife.microsoft.Nuget
import com.manulife.microsoft.NugetConfigFile
import com.manulife.microsoft.NuspecFile
import com.manulife.microsoft.ProjectName
import com.manulife.microsoft.SemVersionJGPFile
import com.manulife.microsoft.XUnitRunnerConsole
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

                        Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        ProjectName.fix('projectName', pipelineParams)
                        ProjectName.fix('testProjectName', pipelineParams)

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)

                        // Versioning file
                        versioningFile = new SemVersionJGPFile(this)
                        versioningFile.createIfMissing(localBranchName)
                        versioningFile.read()
                        projectVersion = versioningFile.getVersion()

                        // Nuget.config file provisioning
                        NugetConfigFile.deleteExistingFiles(this)
                        withCredentials([usernamePassword(credentialsId: pipelineParams.artifactoryCredentialsId,
                                                        usernameVariable: 'ARTIFACTORY_CREDS_USR',
                                                        passwordVariable: 'ARTIFACTORY_CREDS_PSW')]) {
                            NugetConfigFile.createConfig(this,
                                                         "${NugetConfigFile.getFileNameAndPath(this)}",
                                                         pipelineParams.releaseRepo,
                                                         env.ARTIFACTORY_CREDS_USR,
                                                         env.ARTIFACTORY_CREDS_PSW,
                                                         false,
                                                         false)
                        }

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.DOTNETCORE, localBranchName)

                        // SonarQube
                        sonarQubeResult = new SonarQubeResult()
                        SonarQubeRunner.init(this, PipelineType.DOTNETCORE)

                        // MSBuild & DotNet
                        dotNetOpts = "--configuration=Release --no-restore --verbosity=${logger.level.getMSBuildLevel()}"
                        msbuildOpts = "/p:Configuration=Release /p:DebugType=Full -verbosity:${logger.level.getMSBuildLevel()}"
                        dotnetRestoreDebugMode = " -v:${logger.level.getMSBuildLevel()} "
                        if (logger.level <= Level.DEBUG) {
                            dotnetRestoreDebugMode = '--disable-parallel ' + dotnetRestoreDebugMode
                            bat 'dotnet --info --list-runtimes --list-sdks --version'
                        }

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
                        projectVersion = IncreasePatchVersion.perform(this, versioningFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage('Build') {
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        if (SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName)) {
                            MRCommitsList = null
                            if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                                MRCommitsList = GitLabUtils.getCommitsList(this)
                            }

                            SonarQubeRunner.startScan(this, PipelineType.DOTNETCORE, 'dotnet sonarscanner begin', isUnix(), MRCommitsList, projectVersion)
                        }
                    }

                    dir(PROJECT_ROOT_FOLDER) {
                        bat """dotnet restore --configfile "${NugetConfigFile.getFileNameAndPath(this)}" ${dotnetRestoreDebugMode} ${msbuildOpts}"""
                        bat "dotnet build ${dotNetOpts}"
                    }
                }
            }
            stage('Unit Test') {
                when { expression { pipelineParams.testProjectName } }
                environment {
                    TESTS_FOLDER = "${pipelineParams.projectRootFolder}/${pipelineParams.testProjectName}"
                }
                steps {
                    dir("${TESTS_FOLDER}") {
                        script {
                            logger.debug("Content of ${TESTS_FOLDER} folder:") {
                                bat 'dir'
                            }
                        }
                    }

                    script {
                        SonarQubeRunner.fixTestProject(this, PipelineType.DOTNETCORE, "${TESTS_FOLDER}")
                        bat "dotnet test --logger \"xunit;LogFilePath=${SonarQubeRunner.getXUnitResultsFileNameAndPath(this)}\" " +
                                        "--no-restore ${TESTS_FOLDER} /p:CollectCoverage=true /p:CoverletOutputFormat=opencover"
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Quality Assessment (SonarQube)') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        steps {
                            script {
                                bat 'dotnet build-server shutdown'
                                SonarQubeRunner.endScan(this, PipelineType.DOTNETCORE, 'dotnet sonarscanner end')
                                SonarQubeRunner.checkScan(this, sonarQubeResult)
                            }
                        }
                    }
                    stage ('Open-Source Governance (BlackDuck)') {
                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                        steps {
                            withCredentials([usernamePassword(credentialsId: pipelineParams.hubUserPasswordTokenName,
                                                              usernameVariable: 'BLACKDUCK_USR',
                                                              passwordVariable: 'BLACKDUCK_PSW')]) {
                                script {
                                    stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    def blackDuckRunner = new BlackDuckRunner(this,
                                                                              params.forceFullScan,
                                                                              pipelineParams,
                                                                              localBranchName,
                                                                              PipelineType.DOTNETCORE)

                                    String repoUrl = NugetConfigFile.getRepoUrl(this, pipelineParams.releaseRepo)
                                    blackDuckResult = blackDuckRunner.callBlackDuck(
                                        """ "--detect.nuget.excluded.modules=${pipelineParams.hubExcludedModules}"
                                            "--detect.nuget.config.path=${NugetConfigFile.getFileNameAndPath(this)} "
                                            "--detect.nuget.packages.repo.url=${repoUrl}"
                                        """)

                                    if (!blackDuckResult.governanceGatePassed) {
                                        if (Boolean.valueOf(pipelineParams.hubFailPipelineOnFailedOpenSourceGovernance)) {
                                            currentBuild.result = 'FAILED'
                                            error("Failed on Open-Source Governance assessment: ${blackDuckResult.message}")
                                        }
                                        else if (currentBuild.result != 'FAILED') {
                                            currentBuild.result = 'UNSTABLE'
                                            logger.info('Marking the project as UNSTABLE since it failed the open-source governance gate but gating is turned off.')
                                        }
                                    }

                                    stagesExecutionTimeTracker.openSourceGovernanceStageEnd()
                                }
                            }
                        }
                    }
                    stage('Open-Source Governance Assessment (Snyk)') {
                        when { expression { return SnykRunner.isRequested(this, params.forceFullScan, localBranchName) } }
                        environment {
                           PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                        }
                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                dir(PROJECT_ROOT_FOLDER) {
                                    snykRunner.call('')
                                }

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
                    stage('Code Security Assessment (Fortify)') {
                        when { expression { return FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName) } }
                        steps {
                            withCredentials([string(credentialsId: pipelineParams.fortifyTokenName,
                                                    variable: 'FORTIFY_MANAGE_APPLICATION_TOKEN')]) {
                                script {
                                    stagesExecutionTimeTracker.securityCodeScanningStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    fortifyRunner = new FortifyRunner(scriptObj: this,
                                                                      localBranchName: localBranchName,
                                                                      pipelineParams: pipelineParams,
                                                                      buildId: "dotnet-${JOB_BASE_NAME}")
                                    fortifyRunner.init()

                                    fortifyResult = fortifyRunner.run(pipelineParams.fortifyScanTree ?: '.')

                                    if (!fortifyResult.codeSecurityGatePassed) {
                                        if (Boolean.valueOf(pipelineParams.fortifyGating)) {
                                            currentBuild.result = 'FAILED'
                                            error("Failed on Code Security assessment: ${fortifyResult.message}")
                                        }
                                        else if (currentBuild.result != 'FAILED') {
                                            currentBuild.result = 'UNSTABLE'
                                            logger.info('Marking the project as UNSTABLE since it failed the code security gate but gating is turned off.')
                                        }
                                    }

                                    stagesExecutionTimeTracker.securityCodeScanningStageEnd()
                                }
                            }
                        }
                    }
                }
            }
            stage ('Package and Store') {
                when {
                    expression {
                        return ((!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix)/.*'))
                                  && pipelineParams.artifactoryDeploymentPattern != null
                                  && 'MERGE' != env.gitlabActionType
                                  && 'NOTE' != env.gitlabActionType)
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        def artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        artifactoryServer.credentialsId = pipelineParams.artifactoryCredentialsId
                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)

                        // Check that there isn't already an artifact with that commit id in Artifactory
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                                '*.nupkg',
                                                                                pipelineParams.releaseRepo)
                        logger.debug('Folder Content:') { sh 'ls -alR' }

                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                        }
                        else {
                            publishName = pipelineParams.projectDeliverableName ?: pipelineParams.testProjectName

                            if (Boolean.valueOf(pipelineParams.publishApplication)) {
                                // This creates a folder that contains all
                                // the files required to deploy this
                                // application on PCF or other platform.
                                //
                                // Using --no-restore --no-build to avoid
                                // failures accessing nuget.org,
                                //      https://github.com/NuGet/Home/issues/6045
                                bat """dotnet publish "${publishName}" -v:${logger.level.getMSBuildLevel()} --no-build ${msbuildOpts}"""
                            }

                            // Packages the shared library or the published application in a nuget package (.nupkg) file.
                            String nuspecOpt
                            try {
                                NuspecFile nuspecFile = new NuspecFile(this)
                                nuspecFile.read()
                                nuspecOpt = "\"/p:NuspecFile=${WORKSPACE}/${nuspecFile.pathAndName}\""
                            }
                            catch (MicrosoftException mse) {
                                nuspecOpt = ''
                            }

                            // Web projects aren't packable by default.
                            //  https://docs.microsoft.com/en-us/dotnet/core/tools/dotnet-pack?tabs=netcore2x
                            bat """dotnet pack "${publishName}" -v:${logger.level.getMSBuildLevel()} /property:PackageVersion=${projectVersion.toString()} --no-build ${msbuildOpts} ${nuspecOpt} /p:IsPackable=true"""

                            if (logger.level <= Level.DEBUG) {
                                sh "unzip -l \"${publishName}/bin/Release/${publishName}.${projectVersion.toString()}.nupkg\" || :"
                            }

                            artifactoryDeploymentPattern = null
                            if (pipelineParams.artifactoryDeploymentPattern != null) {
                                artifactoryDeploymentPattern = pipelineParams.artifactoryDeploymentPattern
                                // jFrog seems to expect shell wildcards not Ant wildcards (as Archive step does)
                                //      https://github.com/jfrog/build-info/blob/793903f5d6d50f7e384e4b09b41734fa5abd64f9/build-info-extractor/src/main/java/org/jfrog/build/extractor/clientConfiguration/util/spec/SingleSpecDeploymentProducer.java#L114
                                if (artifactoryDeploymentPattern.indexOf('/') == -1 && artifactoryDeploymentPattern.indexOf('\\') == -1) {
                                    artifactoryDeploymentPattern = "${publishName}/bin/Release/" + artifactoryDeploymentPattern
                                }
                            }

                            artifactoryHelper.uploadArtifact(pipelineParams,
                                                             artifactoryDeploymentPattern,
                                                             "${pipelineParams.releaseRepo}/${publishName}/${publishName}-${projectVersion.toString()}.nupkg",
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
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when {
                    expression {
                        return (pipelineParams.deploymentJenkinsJobName
                                && 'MERGE' != env.gitlabActionType
                                && 'NOTE' != env.gitlabActionType)
                    }
                }
                steps {
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                          wait: false,
                          propagate: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                }
            }
        }
        post {
            always {
                script {
                    try {
                        if (binding.hasVariable('artifactoryDeploymentPattern') && artifactoryDeploymentPattern) {
                            archiveArtifacts artifacts: "${artifactoryDeploymentPattern}", allowEmptyArchive: true
                        }
                        archiveArtifacts artifacts: '*.htm', allowEmptyArchive: true
                        archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                        archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true

                        xunit([xUnitDotNet(deleteOutputFiles: true, failIfNotNew: false, pattern: "${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}", skipNoTestFiles: true, stopProcessingIfError: true)])

                        def prefix = ''
                        if (pipelineParams.projectRootFolder != '.') {
                            prefix = "${pipelineParams.projectRootFolder}/"
                        }

                        archiveArtifacts artifacts: "${prefix}${pipelineParams.projectName}" +
                                '/bin/Release/*.nupkg',
                                allowEmptyArchive: true

                        PipelineRunAuditTrailing.log(this)
                        new NotificationsSender(this, pipelineParams).send()
                        GitLabUtils.postStatus(this)
                        new SharedLibraryReport(this).print()
                        GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                        new ProductionSupportInfo(this).print()
                    }
                    catch (err) {
                        logger.warning("Exception caught in post-build steps: (${err})")
                    }
                }
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

    propertiesCatalog.addMandatoryProperty('projectName', 'Missing projectName property value.  The DotNet project name to be used as a default publish name and in archiving Jenkins artifacts.')

    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Setting it to true increments the patch version.', 'false')
    propertiesCatalog.addOptionalProperty('projectRootFolder', 'Defaulting projectRootFolder property to \".\"', '.')
    propertiesCatalog.addOptionalProperty('publishApplication', 'Defaulting publishApplication property to false', 'false')
    propertiesCatalog.addOptionalProperty('testProjectName', 'Defaulting testProjectName property to null', null)
    propertiesCatalog.addOptionalProperty('runtimeProjects', 'Defaulting runtimeProjects property to null (limit coverage by projectName)', null)
    propertiesCatalog.addOptionalProperty('testProjects', 'Defaulting testProjects property to null (reduce coverage by testProjectName and some default test libraries)', null)

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)

    return propertiesCatalog
}
