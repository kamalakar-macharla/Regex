package com.manulife.microsoft

/**
 * Represents the OpenCover tool
 **/
class OpenCover implements Serializable {
    static final String VERSION = '4.7.922'
    static final String OPENCOVER_XML_FILENAME = 'coverage.opencover.xml'

    private final Script scriptObj
    protected final XUnitRunnerConsole xunitRunnerConsole
    protected final String whiteList
    protected final String blackList

    OpenCover(Script scriptObj,
              XUnitRunnerConsole xunitRunnerConsole,
              String runtimeProjects,
              String projectName,
              String testProjects,
              String testProjectName) {
        this.scriptObj = scriptObj
        this.xunitRunnerConsole = xunitRunnerConsole
        whiteList = ((runtimeProjects ?: projectName ?: '')
                    .split(',')
                    .collect { it.trim() }
                    .findAll { it != '' }
                    .collect { "+[${it}*]*" }
                    .join(' '))
        blackList = (((testProjects ?: testProjectName ?: '')
                    .split(',') + ['xunit', 'Moq'])
                    .collect { it.trim() }
                    .findAll { it != '' }
                    .collect { "-[${it}*]*" }
                    .join(' '))
    }

    /**
     * Returns the OpenCover exe file path and name
     **/
    static String getExeNameAndPath(boolean includePackagesFolderInPath) {
        // Somehow for .Net Core we can't specify the outpuf folder & file name so using as default value.
        //  If we change this we have to make sure the dotnet test method + sonarscanner will still result in coverage in SonarQube
        String prefix = ''
        if (includePackagesFolderInPath) {
            prefix = 'packages\\'
        }

        return "${prefix}OpenCover.${VERSION}\\tools\\OpenCover.Console.exe"
    }

    /**
     * Returns the path and name of the file produced by a call to OpenCover
     **/
    static String getOpenCoverXMLFileNameAndPath(String testProjectName) {
        // Somehow for .Net Core we can't specify the outpuf folder & file name so using as default value.
        //  If we change this we have to make sure the dotnet test method + sonarscanner will still result in coverage in SonarQube
        return "${testProjectName}/${OPENCOVER_XML_FILENAME}"
    }

    /**
     * Ensures OpenCover is installed on this build machine.
     * Must be called prior to any call to run()
     **/
    void init() {
        scriptObj.bat "${Nuget.getInstallCmd(scriptObj, 'OpenCover', VERSION)}  || ver>nul"
    }

    /**
     * Runs OpenCover
     **/
    String run(String testFolder, boolean includePackagesFolderInPath) {
        scriptObj.bat getCmd(testFolder, includePackagesFolderInPath)
    }

    /**
     * Returns the full command line to execute OpenCover
     **/
    String getCmd(String testFolder, boolean includePackagesFolderInPath) {
        // Avoiding the all-in broad OpenCover filter with a case-sensitive project name filter, ' +
        //  https://github.com/OpenCover/opencover/issues/771 '
        return "${getExeNameAndPath(includePackagesFolderInPath)} " +
               "-target:\"${xunitRunnerConsole.getExeNameAndPath(includePackagesFolderInPath)}\" " +
               "-targetargs:\"${xunitRunnerConsole.getArgs()}\" " +
               "-filter:\"${whiteList} ${blackList}\" " +
               "-output:${getOpenCoverXMLFileNameAndPath(testFolder)} -oldStyle -register:user"
    }
}