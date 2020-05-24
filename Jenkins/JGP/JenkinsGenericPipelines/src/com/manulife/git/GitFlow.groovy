package com.manulife.git

/**
 * Git Flow
 **/
class GitFlow {
    private final Script scriptObj
    private final GitFlowType gitFlowType

    GitFlow(Script scriptObj, String gitFlowTypeName) {
        this.scriptObj = scriptObj
        this.gitFlowType = GitFlowType.customValueOf(gitFlowTypeName)
    }

    GitFlow(Script scriptObj, GitFlowType gitFlowType) {
        this.scriptObj = scriptObj
        this.gitFlowType = gitFlowType
    }

    String getParentBranch(String childBranch) {
        if (childBranch.startsWith('feature')) {
            if (gitFlowType.hasDevelopBranch && hasBranch('remotes/origin/develop')) {
                return 'develop'
            }
            scriptObj.logger.error('Git repository missing the develop branch.  Will default to master branch.')
        }
        else if (childBranch.startsWith('fix')) {
            if (gitFlowType.hasReleaseBranch && hasBranch('remotes/origin/release')) {
                return 'release'
            }
            scriptObj.logger.error('Git repository missing the release branch.  Will default to master branch.')
        }
        else if (childBranch.startsWith('develop')) {
            if (gitFlowType.hasMasterBranch && hasBranch('remotes/origin/master')) {
                return 'master'
            }
            scriptObj.logger.error('Git repository missing the master branch.')
        }

        // Default parent for hotfix, release and non standard branches
        return 'master'
    }

    boolean isTemporaryBranch(String branchName) {
        return branchName.startsWith('feature') ||
               branchName.startsWith('fix') ||
               branchName.startsWith('hotfix')
    }

    boolean hasBranch(String branchName) {
        return new GitRepo(scriptObj).hasBranch(branchName)
    }
}