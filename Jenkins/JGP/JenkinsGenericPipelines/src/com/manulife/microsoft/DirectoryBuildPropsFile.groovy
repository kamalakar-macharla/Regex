package com.manulife.microsoft

import com.manulife.util.Strings
import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

import com.cloudbees.groovy.cps.NonCPS

/**
 *
 * Utility class that represents a project's Directory.Build.props file.
 *   See: https://thomaslevesque.com/2017/09/18/common-msbuild-properties-and-items-with-directory-build-props/
 *
 **/
class DirectoryBuildPropsFile implements IProjectVersioningFile, Serializable {
    private final Script scriptObj
    private String fileContent
    private String filePathAndName

    DirectoryBuildPropsFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    // Constructor intended to be used only for unit testing purposes
    protected DirectoryBuildPropsFile(Script scriptObj, String fileContent) {
        this.scriptObj = scriptObj
        this.fileContent = fileContent
    }

    String getPathAndName() {
        return this.filePathAndName
    }

    @Override
    void read() {
        this.filePathAndName = findFilePathAndName(scriptObj)
        if (this.filePathAndName == null) {
            throw new MicrosoftException("Couldn't find a .Directory.Build.props file in this project's workspace.")
        }
        this.fileContent = scriptObj.readFile(file: filePathAndName, encoding: 'UTF-8')
    }

    private toJSON() {
        scriptObj.logger.debug("XML File Content: ${this.fileContent}")
        def jsonText = org.json.XML.toJSONObject(Strings.deBOM(this.fileContent)).toString()
        scriptObj.logger.debug("JSON: ${jsonText}")
        return scriptObj.readJSON(text: "${jsonText}")
    }

    @Override
    SemVersion getVersion() {
        def version = SemVersion.parse(scriptObj, toJSON().project.propertygroup.version)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @NonCPS
    @Override
    void setVersion(SemVersion newVersion) {
        def xml = new XmlSlurper().parseText(Strings.deBOM(fileContent))
        xml.project.propertygroup.version.replaceBody newVersion.toString()
        fileContent = groovy.xml.XmlUtil.serialize(xml)
        xml = null
    }

    String getXML() {
        return this.fileContent
    }

    static private String findFilePathAndName(Script scriptObj) {
        def files = scriptObj.findFiles(glob: '**/Directory.Build.props')

        if (files.size() != 1) {
            throw new MicrosoftException('Project must contain one and only one Directory.Build.props file')
        }

        return "${files[0].path}"
    }

    static boolean fileExistsInWorkspace(Script scriptObj) {
        String filePathName = findFilePathAndName(scriptObj)

        if (filePathName) {
            scriptObj.logger.debug("Found Directory.Build.props file: ${filePathName}")
            return true
        }

        scriptObj.logger.warning("Couldn't find Directory.Build.props file in workspace")
        return false
    }

    @Override
    void save() {
        scriptObj.writeFile(file: filePathAndName, text: fileContent, encoding: 'UTF-8')
    }
}
