package com.manulife.microsoft

import com.manulife.git.GitFacade
import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 * Custom file used to deal with Microsoft projects versioning.
 * We first attempted to use the 'standard' Microsoft versioning mechanisms
 *  but we gave up after identifying 4 different ways of doing versioning
 *   + a few variations on those 4 ways (like files being generated or not).
 **/
class SemVersionJGPFile implements IProjectVersioningFile, Serializable {
    private static final String FILE_NAME = 'SemVersion.jgp'
    private final Script scriptObj
    private String fileContent
    SemVersionJGPFile(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    /**
     * Creates the versioning file in the Git Repo if it doesn't exist
     *  in which case it also defaults the project version to 0.0.1.
     **/
    void createIfMissing(String localBranch) {
        // If the file doesn't exist we will assume version 0.0.1 as content
        if (!exists()) {
            this.fileContent = '0.0.1'
            this.save()
            GitFacade gitFacade = new GitFacade(scriptObj)
            gitFacade.addFile(FILE_NAME)
            gitFacade.commitAllAndPush('Adding SemVersion.jgp file to project [ci-skip]', localBranch)
        }
    }

    /**
     * Verifies if the SemVersion.jgp file already exists in the project's workspace
     **/
    boolean exists() {
        return scriptObj.fileExists(FILE_NAME)
    }

    /**
     * Read the content of an existing SemVersion.jgp file
     **/
    @Override
    void read() {
        this.fileContent = scriptObj.readFile(file: FILE_NAME, encoding: 'UTF-8')
    }

    /**
     * Returns the current project version
     **/
    @Override
    SemVersion getVersion() {
        def version = SemVersion.parse(scriptObj, fileContent)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    /**
     * Sets the current project version
     **/
    @Override
    void setVersion(SemVersion newVersion) {
        fileContent = newVersion.toString()
    }

    /**
     * Saves the SemVersion.jgp file to disk.
     **/
    @Override
    void save() {
        scriptObj.writeFile(file: FILE_NAME, text: fileContent, encoding: 'UTF-8')
    }
}
