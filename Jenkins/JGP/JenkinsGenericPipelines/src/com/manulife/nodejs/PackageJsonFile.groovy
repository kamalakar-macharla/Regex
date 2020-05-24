package com.manulife.nodejs

import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 *
 * Utility class that represents a project's Package.json file.
 *
 **/
class PackageJsonFile implements IProjectVersioningFile, Serializable {
    private static final String PACKAGES_JSON_FILE_NAME = 'package.json'
    private final Script scriptObj
    private jsonFileContent

    PackageJsonFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    @Override
    void read() {
        jsonFileContent = scriptObj.readJSON(file: PACKAGES_JSON_FILE_NAME)
    }

    @Override
    void save() {
        if (jsonFileContent == null) {
            throw new IllegalStateException('Must call read() on this object before we can save()')
        }
        scriptObj.writeJSON(file: PACKAGES_JSON_FILE_NAME, json: jsonFileContent, pretty: 4)
    }

    @Override
    SemVersion getVersion() {
        if (jsonFileContent == null) {
            throw new IllegalStateException('Must call read() on this object before we can call getVersion()')
        }
        def version = SemVersion.parse(this.scriptObj, jsonFileContent.version)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @Override
    void setVersion(SemVersion semVersion) {
        if (jsonFileContent == null) {
            throw new IllegalStateException('Must call read() on this object before we can call setVersion()')
        }
        jsonFileContent.version = semVersion.toString()
    }
}