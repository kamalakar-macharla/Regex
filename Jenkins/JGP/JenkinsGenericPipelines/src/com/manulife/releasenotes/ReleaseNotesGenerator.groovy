package com.manulife.releasenotes

class ReleaseNotesGenerator {
    private final Script scriptObj

    private String releaseNotes

    ReleaseNotesGenerator(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    void prepare() {
        scriptObj.logger.info('Checking if release.md exists:')
        // Cloning the Project
        scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.gitLabSSHCredentialsId, keyFileVariable: 'keyfile')]) {
            String getReleaseNotes = "git clone -b ${scriptObj.pipelineParams.toBranch} ${scriptObj.GIT_URL} tmp"
            scriptObj.sshagent([scriptObj.pipelineParams.gitLabSSHCredentialsId]) {
                scriptObj.sh "${getReleaseNotes}"
            }
        }
        scriptObj.logger.info("cloned this:${scriptObj.pipelineParams.toBranch} ${scriptObj.GIT_URL}")
        // If release.md exists, we will append the new notes to the one found here
        scriptObj.dir('tmp') {
            if (scriptObj.fileExists('release.md')) {
                scriptObj.logger.info('Release Notes exist')
                releaseNotes = scriptObj.readFile 'release.md'
            }
            else {
                scriptObj.logger.info('Release Notes do not exist')
            }
        }
    }

    void generate() {
        // Grabbing Python Algorithm
        scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.gitLabSSHCredentialsId, keyFileVariable: 'keyfile')]) {
            String getPythonCode = 'git clone ssh://git@git.platform.manulife.io:2222/CDT_Common/release-notes-generation.git'
            scriptObj.sshagent([scriptObj.pipelineParams.gitLabSSHCredentialsId]) {
                scriptObj.sh "${getPythonCode}"
            }
        }
        // Cloning the Project
        scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.gitLabSSHCredentialsId, keyFileVariable: 'keyfile')]) {
            String getProjectCode = "git clone -b ${scriptObj.pipelineParams.toBranch} ${scriptObj.GIT_URL} main_project"
            scriptObj.sshagent([scriptObj.pipelineParams.gitLabSSHCredentialsId]) {
                scriptObj.sh "${getProjectCode}"
            }
        }
        def repoLocation = null
        scriptObj.dir('main_project') {
            repoLocation = scriptObj.sh (returnStdout: true, script: 'pwd')
        }
        scriptObj.logger.info("${repoLocation}")
        scriptObj.dir('release-notes-generation') {
            // Running Release Notes Generation Code
            scriptObj.sh "python3 promo_release.py ${repoLocation}"
        }
        // Check if Release Notes Generated Or Not
        if (scriptObj.fileExists('release-notes-generation/release.md')) {
            scriptObj.sh "cp release-notes-generation/release.md ${repoLocation}"
            //Append release notes if necessary
            // If true, release.md exists so append the newly generated notes to the existing one
            if (releaseNotes != '') {
                def nwReleaseNotes = scriptObj.readFile "${scriptObj.WORKSPACE}/release-notes-generation/release.md"
                //append the two files
                def appendedReleaseNotes = nwReleaseNotes + '\n' + releaseNotes
                scriptObj.sh "python3 release-notes-generation/generateFile.py '${appendedReleaseNotes}'"
                scriptObj.sh '''
                cp release.md release-notes-generation/release.md
                cp release.md main_project/release.md
                '''
                scriptObj.logger.info('Appended the release notes')
            }
            // Else the file does not exist, continue as usual
            scriptObj.dir('main_project') {
                scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.gitLabSSHCredentialsId, keyFileVariable: 'keyfile')]) {
                    scriptObj.sshagent([scriptObj.pipelineParams.gitLabSSHCredentialsId]) {
                        scriptObj.sh """
                            git add .
                            git commit -m \"Adding Release Notes\"
                            git push origin HEAD:${scriptObj.pipelineParams.toBranch}
                        """
                    }
                }
            }
        }
        else {
            scriptObj.logger.info('Release Notes Not Generated')
        }
        scriptObj.sh 'rm -d -r main_project release-notes-generation'
    }
}