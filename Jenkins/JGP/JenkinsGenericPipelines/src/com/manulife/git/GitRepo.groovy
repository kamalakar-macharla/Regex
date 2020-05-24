package com.manulife.git

/**
 * Represents the Git repository in the current Jenkins workspace.
 **/
class GitRepo {
    private final Script scriptObj

    GitRepo(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    boolean hasBranch(String branchName) {
        String[] existingBranchNames = getBranches()
        for (String existingBranchName : existingBranchNames) {
            scriptObj.logger.debug("Checking if ${existingBranchName} in Git repo is the branch we are looking for (${branchName})")
            if (existingBranchName == branchName) {
                return true
            }
        }

        return false
    }

    String[] getBranches() {
        scriptObj.logger.debug("Result of 'git branch -a' command") {
            scriptObj.sh('git branch -a')
        }
        String branchesStr = scriptObj.sh(script: 'git branch -a', returnStdout: true, label: 'listing Git repo branches')
        String[] branches = branchesStr.split('\n')
        for (int i = 0; i < branches.size(); i++) {
            // Remove empty spaces before and after branch name
            // In output the current branch starts with '* ' so we also have to remove that
            branches[i] = branches[i]?.replaceAll('\\*', '').trim()
        }
        scriptObj.logger.debug("Branches found in Git repo: ${branches}")
        return branches
    }
}