package com.manulife.microsoft

/**
 * This class represents the XUnitRunnerConsole tool.
 * Should be used for .Net Framework (non Core) projects
 **/
class XUnitRunnerConsole implements Serializable {
    static final String VERSION = '2.4.1'
    static final String XUNIT_RESULTS_FILENAME = 'XUnitResults.xml'
    static final String XUNIT_RUNNER_CONSOLE_PACKAGE = 'xunit.runner.console'

    private final Script scriptObj
    private final MSBuild msBuild
    private final String projectRootFolder
    private final String testProjectNameAndFolder
    private final String testProjectName
    private final String xunitTestFlags

    XUnitRunnerConsole(Script scriptObj,
                       MSBuild msBuild,
                       String projectRootFolder,
                       String testProjectNameAndFolder,
                       String testProjectName,
                       String xunitTestFlags) {
        this.scriptObj = scriptObj
        this.msBuild = msBuild
        this.projectRootFolder = projectRootFolder
        this.testProjectNameAndFolder = testProjectNameAndFolder
        this.testProjectName = testProjectName
        this.xunitTestFlags = xunitTestFlags
    }

    /**
     * Installs the tool in the current project's workspace
     **/
    void init() {
        scriptObj.bat "${Nuget.getInstallCmd(scriptObj, XUNIT_RUNNER_CONSOLE_PACKAGE, VERSION)} || ver>nul"
    }

    /**
     * Returns the path and name of the tool.
     **/
    static String getExeNameAndPath(boolean includePackagesFolderInPath) {
        // Somehow for .Net Core we can't specify the outpuf folder & file name so using as default value.
        //  If we change this we have to make sure the dotnet test method + sonarscanner will still result in coverage in SonarQube
        String prefix = ''
        if (includePackagesFolderInPath) {
            prefix = 'packages\\'
        }

        return "${prefix}xunit.runner.console.${VERSION}\\tools\\net452\\xunit.console.exe"
    }

    /**
     * Returns the arguments to be used when calling the tool.
     **/
    String getArgs() {
        return "${testProjectNameAndFolder}\\bin\\${msBuild.buildType}\\${testProjectName}.dll " +
               "-xml ${projectRootFolder}/${XUNIT_RESULTS_FILENAME} -noshadow ${xunitTestFlags} "
    }
}