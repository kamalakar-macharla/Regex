package com.manulife.microsoft

import com.manulife.util.Strings
import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

import com.cloudbees.groovy.cps.NonCPS

/**
 * Utility class that represents a project's .nuspec file.
 **/
class NuspecFile implements IProjectVersioningFile, Serializable {
    private final Script scriptObj
    private String fileContent
    private String filePathAndName

    NuspecFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    // Constructor intended to be used only for unit testing purposes
    protected NuspecFile(Script scriptObj, String fileContent) {
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
            throw new MicrosoftException("Couldn't find a .NuSpec file in this project's workspace.")
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
        def version = SemVersion.parse(scriptObj, toJSON().package.metadata.version)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @NonCPS
    @Override
    void setVersion(SemVersion newVersion) {
        def xml = new XmlSlurper().parseText(Strings.deBOM(fileContent))
        xml.metadata.version.replaceBody newVersion.toString()
        fileContent = groovy.xml.XmlUtil.serialize(xml)
        xml = null
    }

    @NonCPS
    def updateInputPathsInXML(String buildType, String dotNetMajMin) {
        def xml = new XmlSlurper().parseText(Strings.deBOM(fileContent))
        boolean changed = false
        String projFramework = null
        xml.files.file.each { file ->
            //  sed -e "s/\\(<file src=".*[\\\\\\/]bin[\\\\\\/]\)\\(Debug\\|Release\\)\\([\\\\\\/].*\\)/\\1${buildType}\\3/" \\
            //      -e "s/\\(<file src=".*[\\\\\\/]\\)\\(netcoreapp\\)[0-9\.]*\\([\\\\\/].*\\)/\\1\\2${dotNetMajMin}\\3/"
            String fileSrc = "${file['@src']}"
            def m = Strings.match(fileSrc, /(.*[\\\\/]bin[\\\\/])(Debug|Release)([\\\\/].*)/)
            if (m) {
                def updSrc = "${m[0][1]}${buildType}${m[0][3]}"
                if (updSrc != fileSrc) {
                    changed = true
                    fileSrc = updSrc
                }
            }

            m = Strings.match(fileSrc, /(.*[\\\\/])(netcoreapp)([0-9.]+)([\\\\/].*)/)
            if (m) {
                projFramework = "${m[0][2]}${m[0][3]}"
                /*
                 * The dotnet builds generate the build in the csproj-specific
                 * TargetFramework location, so let's report this back instead
                 * of assuming that a nuspec change affects it.
                 *
                 * TODO: consider overriding both the builds with
                 * /p:TargetFramework=netcoreapp${dotNetMajMin} when running
                 * the build and the nuspec's file src location.
                 *
                def updSrc = "${m[0][1]}${m[0][2]}${dotNetMajMin}${m[0][4]}"
                if (updSrc != fileSrc) {
                    changed = true
                    fileSrc = updSrc
                }
                */
            }

            if (changed) {
                file['@src'] = fileSrc
            }
        }

        if (changed) {
            this.fileContent = groovy.xml.XmlUtil.serialize(xml)
            xml = null
            return [true, projFramework]
        }

        xml = null
        return [false, projFramework]
    }

    String getXML() {
        return this.fileContent
    }

    static private String findFilePathAndName(Script scriptObj) {
        def files = scriptObj.findFiles(glob: '**/*.nuspec')

        if (files.size() != 1) {
            throw new MicrosoftException('Project must contain one and only one .nuspec file')
        }

        return "${files[0].path}"
    }

    static boolean fileExistsInWorkspace(Script scriptObj) {
        String nuspecFilePathName = findFilePathAndName(scriptObj)

        if (nuspecFilePathName) {
            scriptObj.logger.debug("Found nuspec file: ${nuspecFilePathName}")
            return true
        }

        scriptObj.logger.warning("Couldn't find nuspec file in workspace")
        return false
    }

    @Override
    void save() {
        scriptObj.writeFile(file: filePathAndName, text: fileContent, encoding: 'UTF-8')
    }
}
