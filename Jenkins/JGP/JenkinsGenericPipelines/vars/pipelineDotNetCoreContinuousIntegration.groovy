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
import com.manulife.microsoft.NugetConfigFile
import com.manulife.microsoft.OpenCover
import com.manulife.microsoft.NuspecFile
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

// TODO:
//   - When new Jenkins build machines are finally available:
//       - Use a Docker Image + Agent type
//       - Modify entries in the environment section.  They should just include the name of the exe since the Docker container should include the path to the tools in its config

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

                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                        artifactoryServer.credentialsId = pipelineParams.artifactoryCredentialsId

                        // BlackDuck
                        blackDuckResult = new BlackDuckResult()

                        // Fortify
                        fortifyRunner = null
                        fortifyResult = new FortifyResult()
                        fortifyRequested = FortifyRunner.isRequested(this, params.forceFullScan, pipelineParams.fortifyTriggers, localBranchName)

                        // Snyk
                        snykRunner = new SnykRunner(this, PipelineType.DOTNETCORE, localBranchName)

                        // SonarQube
                        sonarQubeResult = new SonarQubeResult()
                        sonarQubeRequested = SonarQubeUtils.shouldPerformFullSonarQubeScanning(this, localBranchName)
                        MRCommitsList = null
                        if ('MERGE' == env.gitlabActionType || 'NOTE' == env.gitlabActionType) {
                            MRCommitsList = GitLabUtils.getCommitsList(this)
                        }

                        nugetConfig = pwd(tmp: true) + '\\nuget.config'

                        // DotNet Core v. 2.2.301 showed no
                        // StackOverflowException when parsing IMIT MEF
                        // KafkaProducer.csproj with BlackduckNugetInspector,
                        // https://github.com/blackducksoftware/synopsys-detect/issues/53
                        dotNetMajMin = '2.2'
                        dotNetVersion = "${dotNetMajMin}.301"
                        dotNetRoot = "${ProgramFiles}\\dotnet"
                        // dotNetRoot = "${USERPROFILE}\\.dotnet"

                        // dir(pwd(tmp: true)) {
                        //  curl -sSO https://dot.net/v1/dotnet-install.ps1
                        //  powershell -Command .\dotnet-install.ps1 -InstallDir \"%dr%\" -Version \"%dv%\"
                        // }

                        // https://docs.microsoft.com/en-us/dotnet/core/versions/selection#the-sdk-uses-the-latest-installed-version
                        // TODO: consider checking for the file existence and
                        // installing the required DotNet Core version.
                        def globalJsonContents = """{
  "sdk": {
    "version": "${dotNetVersion}"
  }
}
"""
                        logger.debug("Writing a global.json with a DotNet Core version ${dotNetVersion}...")
                        logger.debug("Contents of global.json: ${globalJsonContents}")
                        writeFile file: "${WORKSPACE}/global.json", text: globalJsonContents, encoding: 'UTF-8'

                        env.DOTNET_HOME = dotNetRoot
                        pathsep = unix ? ':' : ';'
                        env.PATH = dotNetRoot + pathsep + env.PATH

                        buildtype = 'Release'
                        projFramework = "netcoreapp${dotNetMajMin}"

                        // When we start using Docker build machines we will
                        // need to remove all the paths since this will be
                        // defined in the container itself.  We will just leave
                        // the name of the exe to call.

                        // Work around other Visual Studios installed on the
                        // same node which results in a build error,
                        //      MSB4236: The SDK 'Microsoft.NET.Sdk.Web' specified could not be found
                        // as well as
                        //      MSB4236: The SDK 'Microsoft.NET.Sdk' specified could not be found.
                        // https://github.com/Microsoft/msbuild/issues/2532
                        env.MSBUILD_EXE_PATH = "${dotNetRoot}\\sdk\\${dotNetVersion}\\MSBuild.dll"
                        env.MSBuildSDKsPath = "${dotNetRoot}\\sdk\\${dotNetVersion}\\Sdks"

                        xunitXmlTestLoggerVer = '2.1.26'
                        openCoverVer = '4.7.922'
                        openCoverExe = "${USERPROFILE}\\.nuget\\packages\\opencover\\${openCoverVer}\\tools\\OpenCover.Console.exe"
                        reportGeneratorExe = 'E:/build-tools/microsoft/ReportGenerator_3.0.2.0/ReportGenerator.exe'
                        nugetExe = 'E:/build-tools/microsoft/nuget/4.9.1/nuget.exe'
                        msxmlExe = 'E:/build-tools/microsoft/msxsl/msxsl.exe'

                        logger.debug('Environment Variables:') { bat 'set' }
                        dotnetDebugMode = " -v:${logger.level.getMSBuildLevel()} "
                        dotnetRestoreDebugMode = "${dotnetDebugMode} "
                        if (logger.level <= Level.DEBUG) {
                            dotnetRestoreDebugMode = '--disable-parallel ' + dotnetRestoreDebugMode
                        }

                        msbuildOpts = "/p:Configuration=${buildtype} /p:DebugType=Full -verbosity:${logger.level.getMSBuildLevel()}"
                        nuspecFile = new NuspecFile(this)
                        nuspecFile.read()
                        projectVersion = nuspecFile.getVersion()

                        dotNetProjectName = pipelineParams.projectName
                        if (dotNetProjectName?.endsWith('.sln')) {
                            // Some people have .sln in their projectName
                            dotNetProjectName = dotNetProjectName[0..-5]
                        }
                        dotNetTestProjectName = pipelineParams.testProjectName
                        if (dotNetTestProjectName?.endsWith('.sln')) {
                            dotNetTestProjectName = dotNetTestProjectName[0..-5]
                        }
                        publishName = pipelineParams.projectDeliverableName ?: dotNetProjectName
                        openCoverWhiteList = ((pipelineParams.runtimeProjects ?: dotNetProjectName ?: '')
                                    .split(',')
                                    .collect { it.trim() }
                                    .findAll { it != '' }
                                    .collect { "+[${it}*]*" }
                                    .join(' '))
                        openCoverBlackList = (((pipelineParams.testProjects ?: dotNetTestProjectName ?: '')
                                    .split(',') + ['xunit', 'Moq'])
                                    .collect { it.trim() }
                                    .findAll { it != '' }
                                    .collect { "-[${it}*]*" }
                                    .join(' '))

                        artifactoryDeploymentPattern = null
                        artifactoryArchivePattern = null
                        if (pipelineParams.artifactoryDeploymentPattern != null) {
                            artifactoryDeploymentPattern = pipelineParams.artifactoryDeploymentPattern
                            // jFrog seems to expect shell wildcards not Ant wildcards (as Archive step does)
                            //      https://github.com/jfrog/build-info/blob/793903f5d6d50f7e384e4b09b41734fa5abd64f9/build-info-extractor/src/main/java/org/jfrog/build/extractor/clientConfiguration/util/spec/SingleSpecDeploymentProducer.java#L114
                            if (artifactoryDeploymentPattern.indexOf('/') == -1 && artifactoryDeploymentPattern.indexOf('\\') == -1) {
                                artifactoryDeploymentPattern = "${publishName}/bin/${buildtype}/" + artifactoryDeploymentPattern
                            }
                            artifactoryArchivePattern = artifactoryDeploymentPattern
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
                        projectVersion = IncreasePatchVersion.perform(this, nuspecFile)
                        stagesExecutionTimeTracker.increasePatchVersionStageEnd()
                    }
                }
            }
            stage ('Resolve Dependencies & Build') {
                environment {
                    PROJECT_ROOT_FOLDER = "${pipelineParams.projectRootFolder}"
                }
                steps {
                    dir(PROJECT_ROOT_FOLDER) {
                        withCredentials([
                                usernamePassword(credentialsId: pipelineParams.artifactoryCredentialsId,
                                    usernameVariable: 'ARTIFACTORY_CREDS_USR', passwordVariable: 'ARTIFACTORY_CREDS_PSW'),
                                string(credentialsId: pipelineParams.fortifyTokenName,
                                    variable: 'FORTIFY_MANAGE_APPLICATION_TOKEN')]) {
                            script {
                                stagesExecutionTimeTracker.resolveDependenciesAndBuildStageStart()
                                FAILED_STAGE = env.STAGE_NAME

                                NugetConfigFile.createConfig(this, nugetConfig, pipelineParams.releaseRepo,
                                    env.ARTIFACTORY_CREDS_USR, env.ARTIFACTORY_CREDS_PSW)
                                logger.debug("Current PATH = ${env.PATH}")
                                logger.debug("HOMEPATH=${env.USERPROFILE}")
                                logger.debug("APPDATA=${env.APPDATA}")
                                logger.debug("LOCALAPPDATA=${env.LOCALAPPDATA}")
                                logger.debug("TEMP=${env.TEMP}")
                                logger.debug('') {
                                    bat """
                                        dotnet --info --list-runtimes --list-sdks --version
                                        dir "${dotNetRoot}"
                                        dir "${dotNetRoot}\\sdk"
                                        dir "${dotNetRoot}\\sdk\\${dotNetVersion}"
                                        dir "${dotNetRoot}\\sdk\\${dotNetVersion}\\Sdks"
                                        dir
                                    """
                                }

                                boolean contentChanged
                                String projFrameworkFromNuspec
                                (contentChanged, projFrameworkFromNuspec) = nuspecFile.updateInputPathsInXML(buildtype, dotNetMajMin)
                                if (contentChanged) {
                                    logger.debug("Correcting ${nuspecFile.pathAndName}: ${nuspecFile.getXML()}")
                                    nuspecFile.save()
                                }
                                // Trust that the .nuspec's file src="..." attribute reflects TargetFramework in .csproj
                                if (projFrameworkFromNuspec) {
                                    projFramework = projFrameworkFromNuspec
                                }

                                bat """dotnet restore --configfile "${nugetConfig}" ${dotnetRestoreDebugMode} ${msbuildOpts} || exit /b 22
                                       IF EXIST .sonarqube rmdir /q /s .sonarqube
                                       IF EXIST .scannerwork rmdir /q /s .scannerwork
                                """

                                def buildBat = "dotnet msbuild /t:Rebuild ${msbuildOpts}"

                                if (sonarQubeRequested) {
                                    bat """
                                        dotnet tool install --global --configfile "${nugetConfig}" ${dotnetRestoreDebugMode} dotnet-sonarscanner || ver>nul
                                    """
                                    SonarQubeRunner.startScan(this, PipelineType.DOTNETCORE, 'dotnet sonarscanner begin',
                                            unix, MRCommitsList, projectVersion)
                                }

                                bat buildBat

                                stagesExecutionTimeTracker.resolveDependenciesAndBuildStageEnd()
                            } // script
                        } // withCredentials
                    } // dir
                } // steps
            } // stage
            stage('Run Unit Tests') {
                when { expression { dotNetTestProjectName } }
                environment {
                    TEST_DIR = "${pipelineParams.projectRootFolder}\\${dotNetTestProjectName}"
                }
                steps {
                    withCredentials([
                            usernamePassword(credentialsId: pipelineParams.artifactoryCredentialsId,
                                usernameVariable: 'ARTIFACTORY_CREDS_USR', passwordVariable: 'ARTIFACTORY_CREDS_PSW')]) {
                        script {
                            stagesExecutionTimeTracker.runUnitTestsStageStart()
                            FAILED_STAGE = env.STAGE_NAME

                            NugetConfigFile.createConfig(this, nugetConfig, pipelineParams.releaseRepo,
                                env.ARTIFACTORY_CREDS_USR, env.ARTIFACTORY_CREDS_PSW)

                            configFileProvider([configFile(fileId: 'XUnitToNUnit.xslt', targetLocation: "${TEST_DIR}\\XUnitToNUnit.xslt")]) { }
                            bat """
                                    cd "%TEST_DIR%" || exit /b 1
                                    if not exist nuget.config copy /y /b "${nugetConfig}" .
                                    dotnet add package XunitXml.TestLogger --version ${xunitXmlTestLoggerVer} --no-restore || ver>nul
                                    dotnet add package OpenCover --version ${openCoverVer} --no-restore || ver>nul

                                    dotnet restore --configfile "${nugetConfig}" ${dotnetRestoreDebugMode} ${msbuildOpts} || exit /b 44
                                    dotnet msbuild ${msbuildOpts}

                                    @rem dotnet test --no-build ${msbuildOpts} || exit /b 55

                                    @echo.Avoiding the all-in broad OpenCover filter with a case-sensitive project name filter,
                                    @echo.https://github.com/OpenCover/opencover/issues/771

                                    "${openCoverExe}" -target:dotnet.exe \
-targetargs:"test --no-build ${msbuildOpts} --test-adapter-path:. --logger:xunit;LogFilePath=${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}" \
-filter:"${openCoverWhiteList} ${openCoverBlackList}" \
-output:${OpenCover.getOpenCoverXMLFileNameAndPath(TEST_DIR)} -oldStyle -register:user
                                """
                            if (fileExists("${TEST_DIR}\\${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}")) {
                                bat """
                                    "${msxmlExe}" "%TEST_DIR%\\${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}" "%TEST_DIR%\\XUnitToNUnit.xslt" \
-o "${pipelineParams.projectRootFolder}\\NUnitResults.xml"
                                """
                            }
                            else {
                                logger.warning('[Warning] Failed to run opencover.exe')
                            }

                            stagesExecutionTimeTracker.runUnitTestsStageEnd()
                        }
                    }
                }
            }
            stage('Gating') {
                parallel {
                    stage('Code Review') {
                        when { expression { return sonarQubeRequested } }
                        steps {
                            dir("${pipelineParams.projectRootFolder}") {
                                script {
                                    stagesExecutionTimeTracker.codeReviewStageStart()
                                    FAILED_STAGE = env.STAGE_NAME
                                    SonarQubeRunner.endScan(this, PipelineType.DOTNETCORE, 'dotnet sonarscanner end')
                                    SonarQubeRunner.checkScan(this, sonarQubeResult)
                                    stagesExecutionTimeTracker.codeReviewStageEnd()
                                }
                            }
                        }
                    }
                    stage ('Open-Source Governance (BlackDuck)') {
                        when { expression { return BlackDuckRunner.isRequested(this, params.forceFullScan, pipelineParams.hubTriggers, localBranchName) } }
                        steps {
                            withCredentials([
                                    usernamePassword(credentialsId: pipelineParams.artifactoryCredentialsId,
                                        usernameVariable: 'ARTIFACTORY_CREDS_USR', passwordVariable: 'ARTIFACTORY_CREDS_PSW'),
                                    usernamePassword(credentialsId: pipelineParams.hubUserPasswordTokenName,
                                        usernameVariable: 'BLACKDUCK_USR', passwordVariable: 'BLACKDUCK_PSW')]) {
                                script {
                                    stagesExecutionTimeTracker.openSourceGovernanceStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    NugetConfigFile.createConfig(this, nugetConfig, pipelineParams.releaseRepo,
                                        env.ARTIFACTORY_CREDS_USR, env.ARTIFACTORY_CREDS_PSW)

                                    def blackDuckRunner = new BlackDuckRunner(this, params.forceFullScan, pipelineParams, localBranchName,
                                            PipelineType.DOTNETCORE)

                                    String repoUrl = NugetConfigFile.getRepoUrl(this, pipelineParams.releaseRepo)
                                    blackDuckResult = blackDuckRunner.callBlackDuck(
                                        """ "--detect.nuget.excluded.modules=${pipelineParams.hubExcludedModules}"
                                            "--detect.nuget.config.path=${nugetConfig}"
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
                        when { expression { return fortifyRequested } }
                        steps {
                            withCredentials([
                                    string(credentialsId: pipelineParams.fortifyTokenName,
                                        variable: 'FORTIFY_MANAGE_APPLICATION_TOKEN')]) {
                                script {
                                    stagesExecutionTimeTracker.securityCodeScanningStageStart()
                                    FAILED_STAGE = env.STAGE_NAME

                                    def fortifyBuildId = "dotnet-${JOB_BASE_NAME}"
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
                                && (artifactoryDeploymentPattern != null)
                                && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType))
                    }
                }
                steps {
                    withCredentials([
                            usernamePassword(credentialsId: pipelineParams.artifactoryCredentialsId,
                                usernameVariable: 'ARTIFACTORY_CREDS_USR', passwordVariable: 'ARTIFACTORY_CREDS_PSW')]) {
                        script {
                            stagesExecutionTimeTracker.packageAndStoreStageStart()
                            FAILED_STAGE = env.STAGE_NAME
                            NugetConfigFile.createConfig(this, nugetConfig, pipelineParams.releaseRepo,
                                env.ARTIFACTORY_CREDS_USR, env.ARTIFACTORY_CREDS_PSW)

                            ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                            def extension = '*.nupkg'

                            // Check that there isn't already an artifact with that commit id in Artifactory
                            def artifactExists = artifactoryHelper.artifactExists(GIT_COMMIT,
                                                                                  extension,
                                                                                  pipelineParams.releaseRepo)
                            logger.debug('Folder Content:') { sh 'ls -alR' }
                            logger.debug('NuSpec Content:') { sh "cat ${publishName}.nuspec" }

                            if (artifactExists) {
                                logger.warning("[WARNING] Artifactory already contains an artifact for commit ${GIT_COMMIT}.")
                                logger.warning('[WARNING] Will skip the upload to Artifactory.')
                            }
                            else {
                                if (Boolean.valueOf(pipelineParams.publishApplication)) {
                                    // This creates a folder that contains all
                                    // the files required to deploy this
                                    // application on PCF or other platform.
                                    //
                                    // Using --no-restore --no-build to avoid
                                    // failures accessing nuget.org,
                                    //      https://github.com/NuGet/Home/issues/6045
                                    bat """
                                        dotnet publish "${publishName}" ${dotnetDebugMode} --no-build ${msbuildOpts}
                                    """
                                }

                                // Packages the shared library or the published application in a nuget package (.nupkg) file.
                                String nuspecOpt = ''
                                if (nuspecFile.pathAndName != null) {
                                    nuspecOpt = "\"/p:NuspecFile=${WORKSPACE}/${nuspecFile.pathAndName}\""
                                }
                                // Web projects aren't packable by default.
                                //  https://docs.microsoft.com/en-us/dotnet/core/tools/dotnet-pack?tabs=netcore2x
                                bat """
                                    dotnet pack "${publishName}" ${dotnetDebugMode} --no-build ${msbuildOpts} ${nuspecOpt} /p:IsPackable=true
                                """

                                if (logger.level <= Level.DEBUG) {
                                    sh "unzip -l \"${publishName}/bin/${buildtype}/${publishName}.${projectVersion}.nupkg\" || :"
                                }
                                artifactoryHelper.uploadArtifact(pipelineParams,
                                                                 artifactoryDeploymentPattern,
                                                                 "${pipelineParams.releaseRepo}/${publishName}/${publishName}-${projectVersion}.nupkg",
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
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when {
                    expression {
                        return (pipelineParams.deploymentJenkinsJobName
                                    && ('MERGE' != env.gitlabActionType && 'NOTE' != env.gitlabActionType))
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
                        if (artifactoryArchivePattern) {
                            archiveArtifacts artifacts: "${artifactoryArchivePattern}", allowEmptyArchive: true
                        }
                        archiveArtifacts artifacts: '*.htm', allowEmptyArchive: true
                        archiveArtifacts artifacts: '*.pdf', allowEmptyArchive: true
                        archiveArtifacts artifacts: '**/fortify-issues.txt, **/fortify-issues-diff.txt, **/fortify*.pdf', allowEmptyArchive: true

                        def prefix = ''

                        if (pipelineParams.projectRootFolder != '.') {
                            prefix = "${pipelineParams.projectRootFolder}/"
                        }

                        if (dotNetTestProjectName) {
                            nunitResultsNameAndPath = "${prefix}NUnitResults.xml"

                            if (fileExists(nunitResultsNameAndPath)) {
                                nunit testResultsPattern: nunitResultsNameAndPath
                            }
                        }

                        archiveArtifacts artifacts: "${prefix}${dotNetProjectName}" +
                                "/bin/${buildtype}/${projFramework}/${dotNetProjectName}*.*",
                                allowEmptyArchive: true

                        PipelineRunAuditTrailing.log(this)
                        new NotificationsSender(this, pipelineParams).send()
                        GitLabUtils.postStatus(this)
                        new SharedLibraryReport(this).print()
                        GatingReport.getReport(this, blackDuckResult, snykRunner.result, fortifyResult, sonarQubeResult).printText()
                        new ProductionSupportInfo(this).print()
                    }
                    catch (err) {
                        logger.warning("Ignoring a possibly induced error in post-build steps (${err})")
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

