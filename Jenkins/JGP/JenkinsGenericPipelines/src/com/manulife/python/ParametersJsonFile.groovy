package com.manulife.python

import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 *
 * Utility class that represents a project's parameters.json file.
 *
 **/
class ParametersJsonFile implements IProjectVersioningFile, Serializable {
    private static final String PARAMETERS_JSON_FILE_NAME = 'parameters.json'
    private final Script scriptObj
    private jsonFileContent

    ParametersJsonFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    @Override
    void read() {
        jsonFileContent = scriptObj.readJSON(file: PARAMETERS_JSON_FILE_NAME)
    }

    @Override
    void save() {
        scriptObj.writeJSON(file: PARAMETERS_JSON_FILE_NAME, json: jsonFileContent, pretty: 4)
    }

    String getSrc() {
        return jsonFileContent.src
    }

    String getName() {
        return jsonFileContent.name
    }

    @Override
    SemVersion getVersion() {
        def version = SemVersion.parse(this.scriptObj, jsonFileContent.version)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @Override
    void setVersion(SemVersion semVersion) {
        jsonFileContent.version = semVersion.toString()
    }
}