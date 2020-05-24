package com.manulife.gitlab

import com.manulife.pipeline.PipelineType


/**
 * Helper class that contains all the git commands related to code promotions between branches.
 *
 **/
class GitLabCodePromotion implements Serializable {
    Script scriptObj
    PipelineType pipelineType
    def gitLabSSHCredentialsId
    def gitUrl
    def fromBranch
    def toBranch
    def isUnix

    GitLabCodePromotion(Script scriptObj, PipelineType pipelineType, def gitUrl, def gitLabSSHCredentialsId, def fromBranch, def toBranch) {
        this.scriptObj = scriptObj
        this.pipelineType = pipelineType
        this.gitUrl = gitUrl
        this.gitLabSSHCredentialsId = gitLabSSHCredentialsId
        this.fromBranch = fromBranch
        this.toBranch = toBranch
        this.isUnix = scriptObj.isUnix()
    }

    def checkoutSourceRepo() {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            executeGitCmd("checkout ${fromBranch}")
            executeGitCmd('pull')
        }
    }

    def commitSourceRepo(def sourceVersion) {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            executeGitCmd('status')

            gitAddVersioningFile()

            if (PipelineType.DOTNETCORE  == pipelineType ||
                PipelineType.JAVA_GRADLE == pipelineType ||
                PipelineType.JAVA_MAVEN  == pipelineType ||
                PipelineType.NODEJS == pipelineType ||
                PipelineType.PYTHON == pipelineType) {
                executeGitCmd("commit -a -m \"Bumped version number to ${sourceVersion} [ci-skip]\"")
            }

            executeGitCmd("push ${gitUrl} HEAD:${fromBranch}")
        }
    }

    private gitAddVersioningFile() {
        // Commit modified files (Note: npm does that automatically)
        if (PipelineType.JAVA_MAVEN == pipelineType) {
            executeGitCmd('add ./\\pom.xml')
        }
        else if (PipelineType.DOTNETCORE == pipelineType) {
            executeGitCmd('add ./\\*.nuspec')
        }
        else if (PipelineType.JAVA_GRADLE == pipelineType) {
            boolean foundBuildGradleFile

            if (scriptObj.fileExists('build.gradle')) {
                executeGitCmd('add ./\\build.gradle')
                foundBuildGradleFile = true
            }

            if (scriptObj.fileExists('build.gradle.kts')) {
                executeGitCmd('add ./\\build.gradle.kts')
                foundBuildGradleFile = true
            }

            if (!foundBuildGradleFile) {
                scriptObj.logger.error('Could not find a build.gradle or build.gradle.kts file in the project.')
            }
        }
        else if (PipelineType.NODEJS == pipelineType) {
            executeGitCmd('add ./\\package.json')
        }
        else if (PipelineType.PYTHON == pipelineType) {
            executeGitCmd('add ./\\parameters.json')
        }
    }

    def checkoutInNewDestinationBranch(def destinationVersion) {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            executeGitCmd("checkout -b ${toBranch}/${destinationVersion} ${fromBranch}")
        }
    }

    def commitAndPushToNewDestinationBranch(boolean commit, def destinationVersion) {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            if (commit) {
                gitAddVersioningFile()
                executeGitCmd("commit -a -m \"Changed to ${toBranch} version ${destinationVersion}\"")
            }
            executeGitCmd("push -u ${gitUrl} ${toBranch}/${destinationVersion}")
        }
    }

    def mergeAndTagInExistingDestinationBranch(def destinationVersion) {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            // Merge any changes in the destination branch into the source branch
            executeGitCmd("checkout ${fromBranch}")
            executeGitCmd("merge -s ours origin/${toBranch}")
            // Merge any changes in the source branch into the destination branch
            executeGitCmd("checkout ${toBranch}")
            if (scriptObj.pipelineParams.fromSnaphotToReleaseOnToBranch == 'true' || scriptObj.pipelineParams.increaseToBranchPatchVersion == 'true') {
                executeGitCmd("tag -a before_merge_in_${toBranch}_${destinationVersion} -m \"Before merge for version ${destinationVersion}\"")
                executeGitCmd("push --force origin before_merge_in_${toBranch}_${destinationVersion}")
            }

            executeGitCmd("merge ${fromBranch}")
        }
    }

    def commitPushAndTagInExistingDestinationBranch(boolean commit, def destinationVersion) {
        scriptObj.sshagent([gitLabSSHCredentialsId]) {
            if (commit) {
                gitAddVersioningFile()
                executeGitCmd("commit -a -m \"Changed to ${toBranch} version ${destinationVersion}\"")
            }

            executeGitCmd("push ${gitUrl} HEAD:${toBranch}")

            if (scriptObj.pipelineParams.fromSnaphotToReleaseOnToBranch == 'true' || scriptObj.pipelineParams.increaseToBranchPatchVersion == 'true') {
                executeGitCmd("tag -a after_merge_in_${toBranch}_${destinationVersion} -m \"After merge for version ${destinationVersion}\"")
                executeGitCmd("push origin after_merge_in_${toBranch}_${destinationVersion}")
            }
        }
    }

    private void executeGitCmd(def gitCommand) {
        if (isUnix) {
            scriptObj.sh "git ${gitCommand}"
        }
        else {
            scriptObj.bat "git ${gitCommand}"
        }
    }
}