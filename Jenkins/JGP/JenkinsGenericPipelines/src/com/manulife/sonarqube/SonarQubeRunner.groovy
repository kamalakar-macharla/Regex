package com.manulife.sonarqube

import com.manulife.git.GitFlow
import com.manulife.microsoft.NugetConfigFile
import com.manulife.microsoft.OpenCover
import com.manulife.microsoft.XUnitRunnerConsole
import com.manulife.pipeline.PipelineType
import com.manulife.pipeline.PipelineUtils
import com.manulife.util.Strings
import com.manulife.versioning.SemVersion

/**
 *
 * Collection of utilities related to SonarQube
 *
 **/
class SonarQubeRunner {
    static String getXUnitResultsFileNameAndPath(Script scriptObj) {
        return "${scriptObj.WORKSPACE}/${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME}"
    }

    static init(Script scriptObj, PipelineType pipelineType) {
        switch (pipelineType) {
            case PipelineType.DOTNET:
                break
            case PipelineType.DOTNETCORE:
                // Make sure SonarScanner and code coverage tools are installed on that server
                scriptObj.bat """dotnet tool install --configfile "${NugetConfigFile.getFileNameAndPath(scriptObj)}" --global coverlet.console --version 1.7.0 || ver>nul"""
                scriptObj.bat """dotnet tool install --configfile "${NugetConfigFile.getFileNameAndPath(scriptObj)}" --global dotnet-sonarscanner --version 4.8.0 || ver>nul"""
                break
            default:
                scriptObj.error("Unsupported pipeline type: ${pipelineType}")
        }

        // Cleanup temps files from previous runs on Windows
        if (!scriptObj.isUnix()) {
            scriptObj.dir((scriptObj.pipelineParams.projectRootFolder) ?: "${scriptObj.WORKSPACE}") {
                scriptObj.bat 'IF EXIST .sonarqube rmdir /q /s .sonarqube'
                scriptObj.bat 'IF EXIST .scannerwork rmdir /q /s .scannerwork'
            }
        }
    }

    static void fixTestProject(Script scriptObj, PipelineType pipelineType, String testsFolder) {
        switch (pipelineType) {
            case PipelineType.DOTNET:
                break
            case PipelineType.DOTNETCORE:
                scriptObj.dir("${testsFolder}") {
                    // Add project dependencies to allow code coverage computation and export XUnit results
                    scriptObj.bat 'dotnet add package --no-restore XunitXml.TestLogger -v 2.1.26'
                    scriptObj.bat 'dotnet add package --no-restore coverlet.msbuild -v 2.7.0'
                    scriptObj.bat 'dotnet add package --no-restore FluentAssertions -v 5.10.0'
                }

                def dotNetRestoreOpts = "/p:Configuration=Release /p:DebugType=Full -verbosity:${scriptObj.logger.level.getMSBuildLevel()}"
                scriptObj.bat """dotnet restore --configfile "${NugetConfigFile.getFileNameAndPath(scriptObj)}" ${dotNetRestoreOpts} || exit /b 22"""
                break
            default:
                scriptObj.error("Unsupported pipeline type: ${pipelineType}")
        }
    }

    static String getBranchNameParameter(String branchName, String gitlabActionType, String opt) {
        // We do not use branching mechanism for master branch
        if (branchName?.equalsIgnoreCase('master')) {
            return ''
        }
        else if (gitlabActionType == 'MERGE' || gitlabActionType == 'NOTE') {
            return ''
        }

        return "${opt}sonar.branch.name=${branchName}"
    }

    // See this page for details: https://docs.sonarqube.org/latest/branches/overview/
    static String getDestinationBranchParameter(Script scriptObj, String sourceBranchName, String gitlabActionType, String opt) {
        String destinationBranchName = getDestinationBranchName(scriptObj, sourceBranchName, gitlabActionType)

        return (destinationBranchName != null) ? "${opt}sonar.branch.target=${destinationBranchName}" : ''
    }

    // See this page for details: https://docs.sonarqube.org/latest/branches/overview/
    static String getDestinationBranchName(Script scriptObj, String sourceBranchName, String gitlabActionType) {
        // We do not use branching mechanism for master branch, on merge requests & notes
        if (sourceBranchName?.equalsIgnoreCase('master') ||
            gitlabActionType == 'MERGE' ||
            gitlabActionType == 'NOTE') {
            return null
        }

        return new GitFlow(scriptObj, scriptObj.pipelineParams.gitFlowType).getParentBranch(sourceBranchName)
    }

    static void runScan(Script scriptObj,
                        PipelineType pipeType,
                        def sonarRunCmd,
                        Boolean unix,
                        def mrCommitsList,
                        SemVersion projectVersion = null) {
        // Handle corner case where someone creates a merge request that contains no commits.  Nothing to scan.
        if ('MERGE' == scriptObj.env.gitlabActionType || 'NOTE' == scriptObj.env.gitlabActionType) {
            if (mrCommitsList == null || mrCommitsList.length() == 0) {
                scriptObj.logger.warning("Skipping SonarQube scanner because this Merge Request doesn't contain any commit")
                return
            }
        }

        def projectName = Strings.canonicalizeAppKey(PipelineUtils.getJobName(scriptObj))
        SonarQubeWebApi.createProjectIfMissing(scriptObj, projectName)
        def destinationBranchName = getDestinationBranchName(scriptObj, scriptObj.localBranchName, scriptObj.env.gitlabActionType)

        if (destinationBranchName != null && !SonarQubeWebApi.branchExists(scriptObj, projectName, destinationBranchName)) {
            // Was hoping to simply create the missing branch through the SonarQube WebAPI but this is currently not supported.
            scriptObj.error("SonarQube scanning can't take place because the ${projectName} project doesn't have a ${destinationBranchName} branch.  " +
                            "Please run the CI pipeline for the ${destinationBranchName} branch first.")
        }

        String sonarQubeOptions = getSonarQubeOptions(scriptObj, pipeType, mrCommitsList, projectVersion)

        cleanupOldReportFiles(scriptObj, unix)

        scriptObj.logger.debug("SonarQube call parameters: ${sonarQubeOptions}")
        String sonarQubeServerName = EnvironmentVariablesInitializer.getSonarQubeServerName(scriptObj.env.SONAR_ENVIRONMENT)

        scriptObj.withSonarQubeEnv("${sonarQubeServerName}") {
            if (sonarRunCmd instanceof Closure) {
                sonarRunCmd(sonarQubeOptions)
            }
            else {
                def batsh = unix ? scriptObj.&sh : scriptObj.&bat
                batsh "${sonarRunCmd} ${sonarQubeOptions}"
            }
        }
    }

    static void startScan(Script scriptObj,
                          PipelineType pipeType,
                          String sonarBeginCmd,
                          Boolean unix,
                          def mrCommitsList,
                          SemVersion projectVersion = null) {
        runScan(scriptObj, pipeType, sonarBeginCmd, unix, mrCommitsList, projectVersion)
    }

    static void endScan(Script scriptObj,
                        PipelineType pipeType,
                        String sonarEndCmd) {
        // Handle corner case where someone creates a merge request that contains no commits.  Nothing to scan.
        if ('MERGE' == scriptObj.env.gitlabActionType || 'NOTE' == scriptObj.env.gitlabActionType) {
            if (scriptObj.MRCommitsList == null || scriptObj.MRCommitsList.length() == 0) {
                return
            }
        }

        scriptObj.withCredentials([
                scriptObj.string(credentialsId: EnvironmentVariablesInitializer.getSonarQubeTokenName(scriptObj.env.SONAR_ENVIRONMENT),
                                 variable: 'SONAR_TOKEN')]) {
            SonarScannerType scanType
            if (pipeType == PipelineType.DOTNET || pipeType == PipelineType.DOTNETCORE) {
                scanType = SonarScannerType.MSBUILD
            }
            else {
                scanType = SonarScannerType.REGULAR
            }

            String opt = scanType.propertyPrefix
            String sonarQubeServerName = EnvironmentVariablesInitializer.getSonarQubeServerName(scriptObj.env.SONAR_ENVIRONMENT)
            scriptObj.withSonarQubeEnv(sonarQubeServerName) {
                scriptObj.bat "${sonarEndCmd} \"${opt}sonar.login=${scriptObj.env.SONAR_TOKEN}\""
            }
        }
    }

    static void checkScan(Script scriptObj, def sonarQubeResult) {
        if ('MERGE' == scriptObj.env.gitlabActionType || 'NOTE' == scriptObj.env.gitlabActionType) {
            sonarQubeResult.message = 'SonarQube ran a preview of the changes in a merge request.'
            return
        }

        scriptObj.withCredentials([
                scriptObj.string(credentialsId: EnvironmentVariablesInitializer.getSonarQubeTokenName(scriptObj.env.SONAR_ENVIRONMENT),
                                 variable: 'SONAR_TOKEN')]) {
            SonarQubeResult sqResult = SonarQubeCodeQualityGate.check(scriptObj)
            sonarQubeResult.message = sqResult.message
            sonarQubeResult.codeQualityGatePassed = sqResult.codeQualityGatePassed
            sonarQubeResult.sonarBlockerIssueCount = sqResult.sonarBlockerIssueCount
            sonarQubeResult.sonarMajorIssueCount = sqResult.sonarMajorIssueCount
            sonarQubeResult.sonarCriticalIssueCount = sqResult.sonarCriticalIssueCount
        }

        if (!sonarQubeResult.codeQualityGatePassed) {
            if (Boolean.valueOf(scriptObj.pipelineParams.sonarQubeFailPipelineOnFailedQualityGate)) {
                scriptObj.currentBuild.result = 'FAILED'
                scriptObj.error("Failed on Code Quality assessment: ${sonarQubeResult.message}")
            }
            else if (scriptObj.currentBuild.result != 'FAILED') {
                scriptObj.currentBuild.result = 'UNSTABLE'
            }
        }
    }

    static void runnerScan(Script scriptObj, PipelineType pipeType, Boolean unix, def mrCommitsList, def sonarQubeResult, SemVersion projectVersion = null) {
        def sonarQubeRunner
        if (scriptObj.env.SONARQUBE_VERSION == '6') {
            sonarQubeRunner = "${scriptObj.tool 'SonarQube Runner'}/bin/sonar-runner"
        }
        else {
            sonarQubeRunner = "${scriptObj.tool 'SonarQube7 Scanner'}/bin/sonar-scanner"
        }
        runScan(scriptObj, pipeType, "\"${sonarQubeRunner}\"", unix, mrCommitsList, projectVersion)
        checkScan(scriptObj, sonarQubeResult)
    }

    static String getSonarQubeOptions(Script scriptObj,
                                      PipelineType pipeType,
                                      def mrCommitsList,
                                      SemVersion projectVersion) {
        scriptObj.withCredentials([
            scriptObj.usernamePassword(credentialsId: scriptObj.pipelineParams.gitLabAPITokenName,
                                       usernameVariable: 'GITLAB_API_TOKEN_USR',
                                       passwordVariable: 'GITLAB_API_TOKEN_PSW'),
            scriptObj.string(credentialsId: EnvironmentVariablesInitializer.getSonarQubeTokenName(scriptObj.env.SONAR_ENVIRONMENT),
                             variable: 'SONAR_TOKEN')]) {

            def sonarProjectName = Strings.canonicalizeAppKey(PipelineUtils.getJobName(scriptObj))
            def sonarQubeServerURL = EnvironmentVariablesInitializer.getSonarQubeServerURL(scriptObj.env.SONAR_ENVIRONMENT)
            def sonarQubeSources = scriptObj.pipelineParams?.sonarQubeSources?.replace(' ', '')
            def sonarQubeExclusions = scriptObj.pipelineParams?.sonarQubeExclusions?.replace(' ', '')

            SonarScannerType scanType = null
            if (pipeType in [PipelineType.DOTNET, PipelineType.DOTNETCORE]) {
                sonarQubeExclusions += "${scriptObj.pipelineParams.sonarQubeExclusions ? ',' : ''}"
                // TODO: Once we switch to the new pipelines not using NUnit format anymore we will be able to remove the "**/XUnitToNUnit.xslt,**/NUnitResults.xml" exclusions
                sonarQubeExclusions += "**/${OpenCover.OPENCOVER_XML_FILENAME},**/${XUnitRunnerConsole.XUNIT_RESULTS_FILENAME},**/XUnitToNUnit.xslt,**/NUnitResults.xml"
                scanType = SonarScannerType.MSBUILD
            }
            else {
                scanType = SonarScannerType.REGULAR
            }

            String opt = scanType.propertyPrefix
            String projKeyAssign = scanType.projectKeyAssignment
            String projNameAssign = scanType.projectNameAssignment
            String projVersionAssign = scanType.projectVersionAssignment

            String sonarQubeOptions = "\"${opt}sonar.host.url=${sonarQubeServerURL}\" " +
                                        "\"${opt}sonar.login=${scriptObj.env.SONAR_TOKEN}\" " +
                                        "\"${projKeyAssign}${sonarProjectName}\" " +
                                        "\"${projNameAssign}${sonarProjectName}\" " +
                                        "\"${opt}sonar.log.level=${scriptObj.logger.level.sonarQubeLevel}\" "

            if (projectVersion) {
                def sonarProjectVersion = SonarQubeUtils.getProjectVersion(projectVersion).toString()
                sonarQubeOptions += "\"${projVersionAssign}${sonarProjectVersion}\" "
            }
            if (sonarQubeSources) {
                sonarQubeOptions += "\"${opt}sonar.sources=${sonarQubeSources}\" "
            }
            if (sonarQubeExclusions) {
                sonarQubeOptions += "\"${opt}sonar.exclusions=${sonarQubeExclusions}\" "
            }

            sonarQubeOptions += getDestinationBranchParameter(scriptObj,
                                                              scriptObj.localBranchName,
                                                              scriptObj.env.gitlabActionType,
                                                              opt) + ' '

            sonarQubeOptions += getBranchNameParameter(scriptObj.localBranchName,
                                                       scriptObj.env.gitlabActionType,
                                                       opt) + ' '

            if (scriptObj.pipelineParams.testProjectName) {
                if (pipeType in [PipelineType.DOTNET, PipelineType.DOTNETCORE]) {
                    sonarQubeOptions += "\"${opt}sonar.cs.xunit.reportsPaths=${getXUnitResultsFileNameAndPath(scriptObj)}\" "
                    sonarQubeOptions += "${opt}sonar.coverage.exclusions=\"**Test*.cs\" "
                    def testProjectPath = "${scriptObj.pipelineParams.projectRootFolder}/${scriptObj.pipelineParams.testProjectName}"
                    sonarQubeOptions += "\"${opt}sonar.cs.opencover.reportsPaths=${OpenCover.getOpenCoverXMLFileNameAndPath(testProjectPath)}\" "
                }
            }

            if ('MERGE' == scriptObj.env.gitlabActionType || 'NOTE' == scriptObj.env.gitlabActionType) {
                sonarQubeOptions += "\"${opt}sonar.gitlab.user_token=${scriptObj.env.GITLAB_API_TOKEN_PSW}\" " +
                                    "\"${opt}sonar.gitlab.commit_sha=${mrCommitsList}\" " +
                                    "\"${opt}sonar.gitlab.project_id=${scriptObj.env.gitlabMergeRequestTargetProjectId}\" " +
                                    "\"${opt}sonar.gitlab.ref_name=${scriptObj.localBranchName}\" " +
                                    "\"${opt}sonar.gitlab.only_issue_from_commit_file=true\" "
            }

            return sonarQubeOptions
        }
    }

    static void cleanupOldReportFiles(Script scriptObj, Boolean unix) {
        scriptObj.logger.debug('Cleaning up the tree against Sonar plugin report files...')
        def sonarQubeFileSearch = scriptObj.findFiles(glob: '**/report-task.txt')
        if (sonarQubeFileSearch.size()) {
            String filesToDelete = sonarQubeFileSearch.collect { "\"${it.path}\"" }.join(' ')
            if (unix) {
                scriptObj.sh "rm -f ${filesToDelete} || :"
            }
            else {
                scriptObj.bat "del /f ${filesToDelete} || ver>nul"
            }
        }
    }
}
