package com.manulife.versioning

import com.manulife.git.GitFacade
import com.manulife.logger.Level

class IncreasePatchVersion {
    static SemVersion perform(Script scriptObj, IProjectVersioningFile file) {
        SemVersion initialVersion = file.getVersion()
        SemVersion newVersion = initialVersion.getNextPatchVersion()
        GitFacade gitFacade = new GitFacade(scriptObj)
        gitFacade.printStatus(Level.DEBUG)
        file.setVersion(newVersion)
        file.save()
        gitFacade.commitAllAndPush("Bumped version number to ${newVersion.toString()} [ci-skip]", scriptObj.localBranchName)
        scriptObj.logger.info("Updated project version from ${initialVersion.toString()} to ${newVersion.toString()}.")
        return newVersion
    }
}



