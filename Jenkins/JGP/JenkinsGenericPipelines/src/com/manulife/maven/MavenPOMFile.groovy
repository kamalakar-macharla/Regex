package com.manulife.maven

import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 *
 * Utility class that represents a project's .nuspec file.
 *
 **/
class MavenPOMFile implements IProjectVersioningFile, Serializable {
    private final Script scriptObj
    private final String mvnSettings

    MavenPOMFile(Script scriptObj, String mvnSettings) {
        this.scriptObj = scriptObj
        this.mvnSettings = mvnSettings
    }

    @Override
    void read() {
        // Nothing to do
    }

    @Override
    void save() {
        // Nothing to do
    }

    @Override
    SemVersion getVersion() {
        def version = SemVersion.parse(this.scriptObj, scriptObj.readMavenPom().getVersion())
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @Override
    void setVersion(SemVersion semVersion) {
        String cmd = "mvn ${mvnSettings} versions:set versions:update-child-modules -DnewVersion=${semVersion}"
        if (scriptObj.isUnix()) {
            scriptObj.sh "${cmd}"
        }
        else {
            scriptObj.bat "${cmd}"
        }
    }
}
