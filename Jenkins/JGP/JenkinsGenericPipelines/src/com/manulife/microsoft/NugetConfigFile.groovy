package com.manulife.microsoft

import com.manulife.util.Strings

// vim: et:ts=4:sts=4:sw=4:fileencoding=utf-8

/**
 *
 * Represents the NuGet.Config file of a project
 *
 **/
class NugetConfigFile implements Serializable {
    private static filePath

    /**
     * Returns the URL of a NuGet Artifactory repository
     **/
    static String getRepoUrl(Script scriptObj, String repoName) {
        // TODO: determine the artifactory instance's URL via
        // scriptObj.pipelineParams.artifactoryInstance and the artifactory
        // server object.
        return "${scriptObj.env.ARTIFACTORY_URL}/api/nuget/${repoName}"
    }

    /**
     * Returns the standard location for a NuGet.Config file in a project's workspace
     **/
    static final String getFileNameAndPath(Script scriptObj) {
        if (filePath == null) {
            filePath = scriptObj.pwd(tmp: true).replace('\\', '/')
        }
        return "${filePath}/NuGet.Config"
    }

    /**
     * Deletes any pre-existing NuGet.config file(s) in a project's workspace.
     * Such files can exist if in the Git repo of the project
     **/
    static final void deleteExistingFiles(Script scriptObj) {
        def files = scriptObj.findFiles(glob: '**/NuGet.Config')

        scriptObj.logger.info("Found ${files.size()} NuGet.Config files in project to be deleted.")

        for (def file in files) {
            scriptObj.logger.info("Deleting ${file.path}")
            if (scriptObj.isUnix()) {
                scriptObj.sh("rm -f ${file.path}")
            }
            else {

                scriptObj.bat("del /f ${file.path}")
            }
        }
    }

    /**
     * Creates a standard NuGet.Config file in the root of the project's workspace
     **/
    static void createConfig(Script scriptObj,
                             String nugetConfigPath,
                             String repoName,
                             String artUser,
                             String artPassword,
                             boolean cleanupPackages = false,
                             boolean deleteRootNugetConfigFile = true) {
        scriptObj.logger.debug("Writing a NuGet config file ${nugetConfigPath}" +
                " with repo ${repoName} credentials of user" +
                " \"${artUser[0..3]}..${artUser[-4..-1]}\"...")
        String repoUrl = getRepoUrl(scriptObj, repoName)

        String pathLengthWorkaround = ''
        String pathLengthWorkaroundBat = ''
        if (Boolean.valueOf(scriptObj.pipelineParams.nugetPathLengthWorkaround) && Boolean.valueOf(scriptObj.env.NUGET_PATH_LENGTH_WORKAROUND ?: 'true')) {
            // https://github.com/NuGet/Home/issues/3324
            String workspace = scriptObj.env.WORKSPACE

            String packagesDir
            if (workspace[1] == ':') {
                packagesDir = "${workspace[0]}:/"
            }
            else {
                packagesDir = '/'
            }

            packagesDir += "packages/${scriptObj.env.JOB_BASE_NAME}"
            pathLengthWorkaround = """
  <config>
    <add key="repositoryPath" value="${packagesDir}" />
  </config>
"""
            // To simulate the "clean workspace" scenario, call this method
            // with true as the cleanupPackages argument at the beginning of
            // the build.
            //
            // Keep the packages dir and the symlink in subsequent invokations
            // of this method for tasks such as "nuget pack" that reuse the
            // packages.
            String cleanupPackagesBat = cleanupPackages ? """
@echo.Deleting the shorter-length packages directory created as a work-around to https://github.com/NuGet/Home/issues/3324
rmdir /s /q \"${packagesDir}\" || ver>nul
rmdir /s /q packages || ver>nul
""" : ''
            pathLengthWorkaroundBat = """
${cleanupPackagesBat}
mklink /d packages \"${packagesDir}\" || ver>nul
"""
        }

        // Using nuget.exe in creating the nuget.config file has a benefit of
        // storing the password encrypted with a machine-specific key.  Three
        // reasons prevent us from doing so.
        //
        //   a) Nuget.exe will fail to use the machine-specific key referring
        //      to the lack of some "machine" "delegate" privilege.
        //
        //
        //          The requested operation cannot be completed. The computer
        //          must be trusted for delegation and the current user account
        //          must be configured to allow delegation.
        //
        //      https://github.com/NuGet/NuGetGallery/issues/2391#issuecomment-422525637
        //
        //   b) Nuget.exe, as any other command line in Windows, will need
        //      protecting its arguments against the MSC library's parser.  In
        //      particular, we will need to protect double quotes.
        //
        //   c) Nuget.exe has a bug where it interprets some special characters
        //      such as "@" in the password unexpectedly,
        //
        //      https://github.com/NuGet/Home/issues/7707
        String nugetContents = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="${repoName}" value="${repoUrl}" />
  </packageSources>
  <disabledPackageSources>
    <add key="nuget.org" value="true" />
  </disabledPackageSources>
  <packageSourceCredentials>
    <${repoName}>
      <add key="Username" value="${Strings.xmlEncodeAttr(artUser)}" />
      <add key="ClearTextPassword" value="${Strings.xmlEncodeAttr(artPassword)}" />
    </${repoName}>
  </packageSourceCredentials>
  ${pathLengthWorkaround}
</configuration>
"""
        scriptObj.logger.debug("Contents of the nuget.config: ${nugetContents}")
        scriptObj.writeFile(file: nugetConfigPath,
                text: nugetContents)
        String deleteRootNugetConfigFileStr = ''
        if (deleteRootNugetConfigFile) {
            deleteRootNugetConfigFileStr = 'nuget.config'
        }

        scriptObj.bat(returnStdout: false,
            script: """\
@echo.Deleting Nuget configuration files which DotNet/MSBuild/NuGet keep reading even when told to use another file.
del /f /q %appdata%\\nuget\\nuget.config ${deleteRootNugetConfigFileStr} 2> nul || ver>nul
${pathLengthWorkaroundBat}
""")
        scriptObj.logger.debug('Done setting up a NuGet feed.')
    }
}
