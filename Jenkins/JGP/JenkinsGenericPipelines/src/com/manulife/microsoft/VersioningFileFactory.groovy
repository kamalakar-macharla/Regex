package com.manulife.microsoft

import com.manulife.versioning.IProjectVersioningFile

//This Class handles the logic for finding and reading one of the 4(2 currently) files that are used
//for versioning in your dotnet application.
class VersioningFileFactory {
    static IProjectVersioningFile getVersioningFile(Script scriptObj) {
        def versioningFile = null
        def fileCS = scriptObj.findFiles(glob: '**/AssemblyInfo.cs')
        if (fileCS.size() != 0) {
            versioningFile = new AssemblyInfoFile(scriptObj)
            scriptObj.logger.info('Project does contain a AssemblyInfo.cs file.')
        }
        else {
            versioningFile = new NuspecFile(scriptObj)
            scriptObj.logger.info('Project does contain a nuspec file.')
        }
        return versioningFile
    }
}
