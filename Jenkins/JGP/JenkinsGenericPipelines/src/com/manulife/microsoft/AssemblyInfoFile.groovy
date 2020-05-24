package com.manulife.microsoft

import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 *
 * Utility class that represents a project's Assemblyinfo.cs file.
 *
 **/
class AssemblyInfoFile implements IProjectVersioningFile, Serializable {
    private final Script scriptObj
    private String fileContent
    private String filePathAndName
    private String fileUpload

    AssemblyInfoFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    // Constructor intended to be used only for unit testing purposes
    protected AssemblyInfoFile(Script scriptObj, String fileContent) {
        this.scriptObj = scriptObj
        this.fileContent = fileContent
        this.fileUpload = fileUpload
    }

    String getPathAndName() {
        return this.filePathAndName
    }

    static private String findFilePathAndName(Script scriptObj) {
        def files = scriptObj.findFiles(glob: '**/AssemblyInfo.cs')

        if (files.size() != 1) {
            throw new MicrosoftException('Project must contain one AssemblyInfo.cs file')
        }

        return "${files[0].path}"
    }

    @Override
    void read() {
        this.filePathAndName = findFilePathAndName(scriptObj)
        if (this.filePathAndName == null) {
            throw new MicrosoftException("Couldn't find a AssemblyInfo.cs file in this project's workspace.")
        }
        this.fileContent = scriptObj.readFile(file: filePathAndName, encoding: 'UTF-8')
    }

    @Override
    SemVersion getVersion() {
        def versionm = fileContent =~ (/AssemblyVersion\(([^)]+)\)/)
        def versionb = versionm[0][1].replace(("\""), "")
        scriptObj.logger.info("Version: ${versionb.toString()}")
        return SemVersion.parse(this.scriptObj, versionb)
    }

    @Override
    void setVersion(SemVersion newVersion) {
        def updatedVersion = newVersion.toString()
        scriptObj.logger.debug("File Content Before: ${fileContent}")
        def fileUpload = fileContent.replace("[assembly: AssemblyVersion(\"${version}\")]", "[assembly: AssemblyVersion(\"${updatedVersion}\")]")
        this.fileContent = fileUpload
        scriptObj.logger.debug("File Content After: ${fileContent}")
    }

    @Override
    void save() {
        scriptObj.writeFile(file: filePathAndName, text: fileContent, encoding: 'UTF-8')
    }
}