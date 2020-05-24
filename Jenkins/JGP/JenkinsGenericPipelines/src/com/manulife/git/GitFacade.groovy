package com.manulife.git

import com.manulife.logger.Level

/**
 *
 * Simplifies the interactions with Git
 *
 **/
class GitFacade {
    private final Script scriptObj
    private final String sshCredentialsVaultId

    GitFacade(Script scriptObj) {
        this.scriptObj = scriptObj
        this.sshCredentialsVaultId = scriptObj.pipelineParams.gitLabSSHCredentialsId
    }

    void printStatus(Level level) {
        scriptObj.sshagent([sshCredentialsVaultId]) {
            scriptObj.logger.log(level, 'Git status:') {
                if (scriptObj.isUnix()) {
                    scriptObj.sh('git status')
                }
                else {
                    scriptObj.bat('git status')
                }
            }
        }
    }

    void addFile(String fileName) {
        try {
            scriptObj.sshagent([sshCredentialsVaultId]) {
                if (scriptObj.isUnix()) {
                    scriptObj.sh("git add ${fileName}")
                }
                else {
                    scriptObj.bat("git add ${fileName}")
                }
            }
        }
        catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
            throw new GitException("Unable to find a valid entry in the credential vault with id = ${scriptObj.pipelineParams.gitLabSSHCredentialsId}, error: ${e.message}")
        }
        catch (e) {
            throw new GitException("Unable to execute Git commands due to the unexpected error(s), error: ${e.message}")
        }
    }

    void commitAllAndPush(String commitMsg, String branch) {
        try {
            scriptObj.sshagent([sshCredentialsVaultId]) {
                if (scriptObj.isUnix()) {
                    scriptObj.sh("git commit -a -m \"${commitMsg}\"")
                    scriptObj.sh("git push origin HEAD:${branch}")
                }
                else {
                    scriptObj.bat("git commit -a -m \"${commitMsg}\"")
                    scriptObj.bat("git push origin HEAD:${branch} || ver>nul")
                }
            }
        }
        catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
            throw new GitException("Unable to find a valid entry in the credential vault with id = ${scriptObj.pipelineParams.gitLabSSHCredentialsId}, error: ${e.message}")
        }
        catch (e) {
            throw new GitException("Unable to execute Git commands due to the unexpected error(s), error: ${e.message}")
        }
    }
}