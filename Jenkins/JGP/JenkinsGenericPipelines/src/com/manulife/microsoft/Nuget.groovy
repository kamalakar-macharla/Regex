package com.manulife.microsoft

/**
 * Represents the Nuget.exe tool
 **/
class Nuget implements Serializable {
    public static final String EXE = 'E:/build-tools/microsoft/nuget/4.9.1/nuget.exe'

    /**
     * Returns a full dotnet restore command
     */
    static final String getRestoreCmd(Script scriptObj, String solutionDirectory = null) {
        String retval = """ "${EXE}" restore -ConfigFile "${NugetConfigFile.getFileNameAndPath(scriptObj)}" """
        if (solutionDirectory) {
            retval += "-SolutionDirectory ${solutionDirectory}"
        }
        return retval
    }

    /**
     * Returns a full dotnet install command
     */
    static final String getInstallCmd(Script scriptObj, String packageName, String packageVersion) {
        return "${EXE} install -ConfigFile ${NugetConfigFile.getFileNameAndPath(scriptObj)} ${packageName} -Version ${packageVersion}"
    }
}
