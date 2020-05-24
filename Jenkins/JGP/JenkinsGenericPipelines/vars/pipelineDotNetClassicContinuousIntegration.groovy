import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.banner.Banner
import com.manulife.blackduck.BlackduckPropertiesCalalogBuilder
import com.manulife.blackduck.BlackDuckRunner
import com.manulife.blackduck.BlackDuckResult
import com.manulife.gating.GatingReport
import com.manulife.git.GitPropertiesCatalogBuilder
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.fortify.FortifyPropertiesCalalogBuilder
import com.manulife.fortify.FortifyResult
import com.manulife.fortify.FortifyRunner
import com.manulife.logger.Logger
import com.manulife.microsoft.MSBuild
import com.manulife.microsoft.Nuget
import com.manulife.microsoft.NugetConfigFile
import com.manulife.microsoft.OpenCover
import com.manulife.microsoft.SemVersionJGPFile
import com.manulife.microsoft.XUnitRunnerConsole
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.snyk.SnykPropertiesCatalogBuilder
import com.manulife.snyk.SnykRunner
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.sonarqube.SonarQubeRunner
import com.manulife.sonarqube.SonarQubeUtils
import com.manulife.sonarqube.SonarQubePropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.Shell
import com.manulife.versioning.IncreasePatchVersion

def call(Map configuration) {
    pipeline {
        agent {
            label {
                label "${configuration.jenkinsJobInitialAgent}"
                // We use a custom workspace to help reducing the length of the Workspace's path.
                //  Many old projects would exceed the 256 chars limit for path + filenames otherwise.
                customWorkspace "e:/w/${JOB_BASE_NAME}"
            }
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

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-ci.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        env.PATH = "${tool pipelineParams.msBuildVersion};${env.PATH}"

                        msBuild = new MSBuild(this)
                        msBuild.init(pipelineParams.msBuildVersion, pipelineParams.buildType)

                        Shell.fixAndPropagateJavaHome(this, isUnix())
                        Shell.trustZscalerInJava(this, isUnix())

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        artifactoryServer.credentialsId = pipelineParams.artifactoryCredentialsId

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
                                                         true,
                                                         false)
                        }

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.DOTNET, localBranchName)

                        // SonarQube
                        sonarQubeResult = new SonarQubeResult()
                        SonarQubeRunner.init(this, PipelineType.DOTNET)
                        SONARQUBE_FOR_MSBUILD_EXE = tool(pipelineParams.sonarQubeScannerVersion) + '\\SonarQube.Scanner.MSBuild.exe'

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
            stage ('Resolve Dependencies & Build') {
                steps {
                    script {
                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        dir("${pipelineParams.projectRootFolder}") {
                            bat Nuget.getRestoreCmd(this)

                            if (SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName)) {
                                MRCommitsList = null
                                if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                                    MRCommitsList = GitLabUtils.getCommitsList(this)
                                }
                                SonarQubeRunner.startScan(this, PipelineType.DOTNET, "\"${SONARQUBE_FOR_MSBUILD_EXE}\" begin", isUnix(), MRCommitsList, projectVersion)
                            }

                            if (FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName)) {
                                withCredentials([string(credentialsId: pipelineParams.fortifyTokenName,
                                                        variable: 'FORTIFY_MANAGE_APPLICATION_TOKEN')]) {
                                    fortifyRunner = new FortifyRunner(scriptObj: this,
                                                                    localBranchName: localBranchName,
                                                                    pipelineParams: pipelineParams,
                                                                    buildId: "dotnetclassic-${JOB_BASE_NAME}")
                                    fortifyRunner.init()

                                    if (pipelineParams.fortifyScanTree) {
                                        bat msBuild.rebuildCmd
                                    }
                                    else {
                                        fortifyRunner.translateOnly(msBuild.rebuildCmd)
                                    }
                                }
                            }
                            else {
                                bat msBuild.rebuildCmd
                            }
                        }

                        stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                    }
                }
            }
            stage('Run Unit Tests') {
                when { expression { pipelineParams.testProjectName } }
                environment {
                    TEST_PROJECT_NAME_AND_FOLDER = "${pipelineParams.projectRootFolder}/${pipelineParams.testProjectName}"
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        XUnitRunnerConsole xunitRunnerConsole =
                                new XUnitRunnerConsole(this,
                                                       msBuild,
                                                       pipelineParams.projectRootFolder,
                                                       TEST_PROJECT_NAME_AND_FOLDER,
                                                       pipelineParams.testProjectName,
                                                       pipelineParams.xunitTestFlags)
                        xunitRunnerConsole.init()

                        OpenCover openCover = new OpenCover(this,
                                                            xunitRunnerConsole,
                                                            pipelineParams.runtimeProjects,
                                                            pipelineParams.projectName,
                                                            pipelineParams.testProjects,
                                                            pipelineParams.testProjectName)
                        openCover.init()

                        dir(TEST_PROJECT_NAME_AND_FOLDER) {
                            bat "${Nuget.getRestoreCmd(this, '"%WORKSPACE%\\%PROJECT_ROOT_FOLDER%"')}"
                            bat "${msBuild.rebuildCmd} || exit /b 44"
                        }

                        openCover.run(TEST_PROJECT_NAME_AND_FOLDER, 'true' == pipelineParams.nugetPathLengthWorkaround)
                        stagesExecutionTimeTracker.runUnitTestsStageEnd()
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName) } }
                        steps {
                            dir("${pipelineParams.projectRootFolder}") {
                                script {
                                    stagesExecutionTimeTracker.codeReviewStageStart()
                                    FAILED_STAGE = env.STAGE_NAME
                                    SonarQubeRunner.endScan(this, PipelineType.DOTNET, "\"${SONARQUBE_FOR_MSBUILD_EXE}\" end")
                                    SonarQubeRunner.checkScan(this, sonarQubeResult)
                                    stagesExecutionTimeTracker.codeReviewStageEnd()
                                }
                            }
                        }
                    }
                    stage ('Open-Source Governance (BlackDuck)') {
                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                        steps {
                            script {
                                stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName, PipelineType.DOTNET)

                                String repoUrl = NugetConfigFile.getRepoUrl(this, pipelineParams.releaseRepo)

                                withCredentials([usernamePassword(credentialsId: pipelineParams.hubUserPasswordTokenName,
                                                                  usernameVariable: 'BLACKDUCK_USR',
                                                                  passwordVariable: 'BLACKDUCK_PSW')]) {
                                    blackDuckResult = blackDuckRunner.callBlackDuck(
                                        """ "--detect.nuget.excluded.modules=${pipelineParams.hubExcludedModules}"
                                            "--detect.nuget.config.path=${NugetConfigFile.getFileNameAndPath(this)}"
                                            "--detect.nuget.packages.repo.url=${repoUrl}"
                                        """)
                                }

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
                        when { expression { return (fortifyRunner != null) } }
                        steps {
                            script {
                                stagesExecutionTimeTracker.securityCodeScanningStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                withCredentials([string(credentialsId: pipelineParams.fortifyTokenName,
                                                        variable: 'FORTIFY_MANAGE_APPLICATION_TOKEN')]) {
                                    fortifyResult = fortifyRunner.run(pipelineParams.fortifyScanTree)
                                }

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
            stage ('Package and Store') {
                when {
                    expression {
                        return (!env.BRANCH_NAME || !env.BRANCH_NAME.matches('(feature|fix).*')) &&
                               pipelineParams.artifactoryDeploymentPattern != null &&
                               'MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType
                    }
                }
                steps {
                    script {
                        stagesExecutionTimeTracker.packageAndStoreStageStart()
                        FAILED_STAGE = env.STAGE_NAME

                        ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)

                        // Check that there isn't already an artifact with that commit id in Artifactory
                        def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT, pipelineParams)

                        if (artifactExists) {
                            logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                            logger.warning('[WARNING] Will skip the upload to Artifactory.')
                        }
                        else {
                            // Only the develop branch should have the option of being built as pre-release snapshot
                            def buildSnapshot = localBranchName.toLowerCase().contains('dev') && pipelineParams.buildSnapshot

                            // Create a NuGet package
                            if (pipelineParams.projectDeliverableName) {

                                nugetOptions = "-Prop Configuration=${msBuild.buildType}"
                                if (buildSnapshot) {
                                    nugetOptions += " -suffix snapshot"
                                }

                                // If specified, we will include symbols as a part of the nuget package to ease debugging
                                if (pipelineParams.includeSymbols) {
                                    nugetOptions += " -symbols"
                                }

                                // Packages the shared library or the published application in a nuget package (.nupkg) file.
                                bat """
                                    "${Nuget.EXE}" pack -ConfigFile "${NugetConfigFile.getFileNameAndPath(this)}" "${pipelineParams.projectDeliverableName}" ${nugetOptions}
                                """

                                // packaging with the "symbols" parameter will result in the building of two nuget packages
                                // one with symbols in the name and one without.  We need to discard the version without
                                // symbols in the name and drop symbols from the name of the symbols version.  Unfortunately
                                // the only way to do this rename correctly is with Powershell
                                if (pipelineParams.includeSymbols) {
                                    PowerShell("Get-ChildItem *.symbols.nupkg | % { move-item \$_ \$_.Name.Replace('symbols.', '') -Force }")
                                }
                            }

                            if (pipelineParams.windowsInstallerProjects) {
                                // DisableOutOfProcBuild.exe has to be called from the folder in which it is installed.
                                // Otherwise the program will fail to write to the registry
                                def disableOutOfProcFolder = 'C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Professional\\Common7\\' +
                                                                     'IDE\\CommonExtensions\\Microsoft\\VSI\\DisableOutOfProcBuild'
                                def disableOutOfProcExe = 'DisableOutOfProcBuild.exe'

                                // Any given windows installer can fail with a 8000000A race condition error
                                // To prevent, call the DisableOutOfProc tool in VS 2017
                                // Setting DWORD value 'EnableOutOfProcBuild' to '0' on registry key with path
                                // 'HKEY_CURRENT_USER\SOFTWARE\Microsoft\VisualStudio\15.0_<id>_Config\MSBuild'...
                                // Note that this applications CWD must be the folder containing the application,
                                // otherwise it will fail to alter the registry
                                bat "pushd \"${disableOutOfProcFolder}\" && ${disableOutOfProcExe} && popd || popd"

                                // loop through the windows installer projects and build them all
                                for (String project: pipelineParams.windowsInstallerProjects.split(',')) {
                                    bat "\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Professional\\Common7\\IDE\\Devenv.com\" ${project} /Rebuild ${msBuild.buildType}"
                                }

                                // Restore DisableOutOfProc build state
                                // Setting DWORD value 'EnableOutOfProcBuild' to '1' on registry key with path
                                // 'HKEY_CURRENT_USER\SOFTWARE\Microsoft\VisualStudio\15.0_<id>_Config\MSBuild'...
                                bat "pushd \"${disableOutOfProcFolder}\" && ${disableOutOfProcExe} undo && popd || popd"

                                // Allow for appending of snapshot suffix to the build number so that MSIs following
                                // the same signaling rules as nupkgs
                                def snapshotSuffix = ""
                                if (buildSnapshot) {
                                    snapshotSuffix = "-snapshot"
                                }

                                // TODO: Should't we use the projectVersion variable value instead of BUILD_NUMBER ???
                                // in the case of an MSI, the projectVersion is equivalent to the Jenkins build number
                                projectVersion = "${BUILD_NUMBER}${snapshotSuffix}"

                                // Append Jenkins Build Number to the installer(s) as a way of versioning the package
                                bat "for /R %%a in (*.msi) do ren \"%%~a\" \"%%~na.${projectVersion}%%~xa\""
                            }

                            artifactoryHelper.uploadArtifact(pipelineParams,
                                    pipelineParams.artifactoryDeploymentPattern,
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
                when { expression { return pipelineParams.deploymentJenkinsJobName && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType) } }
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
                archiveArtifacts artifacts: "${pipelineParams.artifactoryDeploymentPattern}", allowEmptyArchive: true
                archiveArtifacts artifacts: '*.htm', allowEmptyArchive: true
                archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true

                xunit([xUnitDotNet(deleteOutputFiles: true, failIfNotNew: false, pattern: "${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}", skipNoTestFiles: true, stopProcessingIfError: true)])

                script {
                    def prefix = ''
                    if (pipelineParams.projectRootFolder != '.') {
                        prefix = "${pipelineParams.projectRootFolder}/"
                    }

                    archiveArtifacts artifacts: "${prefix}${pipelineParams.projectName}" +
                            "/bin/${msBuild.buildType}/${pipelineParams.projectName}*.*",
                            allowEmptyArchive: true

                    new SharedLibraryReport(this).print()
                    GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                    new ProductionSupportInfo(this).print()
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
                }
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

    propertiesCatalog.addMandatoryProperty('projectName', 'Missing projectName property value.  Should be set to the DotNet project name.')

    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null', null)
    propertiesCatalog.addOptionalProperty('increasePatchVersion', 'Defaulting increasePatchVersion to false.  Set to true if you want the promotion to increment the patch version.', 'false')
    propertiesCatalog.addOptionalProperty('msBuildVersion', 'MSBuild version.  Defaults to: MSBuild 15', 'MSBuild 15')
    propertiesCatalog.addOptionalProperty('projectRootFolder', 'Defaulting projectRootFolder property to \".\"', '.')
    propertiesCatalog.addOptionalProperty('sonarQubeScannerVersion', 'SonarQube Scanner for MSBuild version.  Defaults to sonar-scanner-msbuild-4', 'sonar-scanner-msbuild-4')
    propertiesCatalog.addOptionalProperty('testProjectName', 'Defaulting testProjectName property to null', null)
    propertiesCatalog.addOptionalProperty('runtimeProjects', 'Defaulting runtimeProjects property to null (limit coverage by projectName)', null)
    propertiesCatalog.addOptionalProperty('testProjects', 'Defaulting testProjects property to null (reduce coverage by testProjectName and some default test libraries)', null)
    propertiesCatalog.addOptionalProperty('xunitTestFlags', 'Defaulting xunitTestFlags property to \"\"', '')
    propertiesCatalog.addOptionalProperty('windowsInstallerProjects', 'Defaulting windowsInstallerProjects property to null', null)
    propertiesCatalog.addOptionalProperty('nugetPathLengthWorkaround', 'Defaulting nugetPathLengthWorkaround property to \"true\"', 'true')
    propertiesCatalog.addOptionalProperty('buildSnapshot', 'Defaulting buildSnapshot property to false. Set to true if you want snapshot pre-releases created.', 'false')
    propertiesCatalog.addOptionalProperty('includeSymbols', 'Defaulting includeSymbols property to false. Set to true if you want symbols included with nupkgs.', 'false')
    propertiesCatalog.addOptionalProperty('buildType', 'Defaulting buildType to Release.  Set to Debug for debug builds.', 'Release')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    SnykPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)
    SonarQubePropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)

    return propertiesCatalog
}

def PowerShell(psCmd) {
    psCmd=psCmd.replaceAll("%", "%%")
    def powershellExe = 'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
    bat "${powershellExe} -NonInteractive -ExecutionPolicy Bypass -Command \"\$ErrorActionPreference='Stop';[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;$psCmd;EXIT \$global:LastExitCode\""
}