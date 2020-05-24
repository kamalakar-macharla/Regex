package com.manulife.gitlab

import com.manulife.logger.Level
import com.manulife.util.Shell

/**
 * GitLab utilities.
 **/
class GitLabUtils {
    // The wait time in seconds for any response from Gitlab
    static final int MAXTIME = 15

    // The number of tries of critical operations such as downloading a wrapper script
    static final int MAXTRIES = 3

    private static slice(String[] pieces, int begin, int end) {
        def sliced = []

        int endPos = end
        if (endPos < 0) {
            endPos += pieces.length
        }

        for (int i = begin; i <= endPos; i++) {
            sliced.add(pieces[i])
        }

        return sliced
    }

    static String getLocalBranchName(Script scriptObj) {
        scriptObj.logger.debug("gitlabActionType = ${scriptObj.env.gitlabActionType ?: '???'}")
        scriptObj.logger.debug("gitlabSourceBranch = ${scriptObj.env.gitlabSourceBranch ?: '???'}")
        scriptObj.logger.debug("gitlabTargetBranch = ${scriptObj.env.gitlabTargetBranch ?: '???'}")
        scriptObj.logger.debug("GIT_BRANCH = ${scriptObj.env.GIT_BRANCH ?: '???'}")
        scriptObj.logger.debug("BRANCH_NAME = ${scriptObj.env.BRANCH_NAME ?: '???'}")

        // Make sure we do not reintroduce a bug here...
        // When a MR is open for a feature branch but is still Work in Progress (the MR name begins with "WIP:")
        //    * Committing code to the feature branch will result into triggering the feature branch pipeline with the following values:
        //          * GIT_BRANCH = develop
        //          * BRANCH_NAME = feature/CDT...
        //    * So, if we use the GIT_BRANCH as the local branch we will end-up updating the develop branch in SonarQube...
        // When a MR is open for a feature branch and NOT Work in Progress (the MR name DOESN't begin with "WIP:")
        //    * Committing code to the feature branch will also result into triggering the develop branch pipeline with the following values:
        //          * GIT_BRANCH = develop
        //          * BRANCH_NAME = feature/CDT...
        //    * But in that case we are just uploading the results to the GitLab MR (in the discussion) and not updating SonarQube.
        String branchName
        if (scriptObj.env.gitlabTargetBranch) {
            scriptObj.logger.debug('Will use the value of scriptObj.env.gitlabTargetBranch as the current branch name.')
            branchName = scriptObj.env.gitlabTargetBranch
        }
        else if (scriptObj.env.BRANCH_NAME?.matches('(feature|fix|hotfix).*')) {
            scriptObj.logger.debug('Will use the value of scriptObj.env.BRANCH_NAME as the current branch name.')
            branchName = scriptObj.env.BRANCH_NAME
        }
        else if (scriptObj.env.GIT_BRANCH) {
            scriptObj.logger.debug('Will use the value of scriptObj.env.GIT_BRANCH as the current branch name.')
            branchName = scriptObj.env.GIT_BRANCH
        }

        if (!branchName) {
            scriptObj.error('Neither the GitLab server nor the Jenkins Git plugin could provide a branch name.  ' +
                            'Posting details about this job and the failure in a Yammer group CDT DevOps - Support/Operations may help in resolving the issue.')
        }
        // Depending on how the job is triggerered you will have something like origin/dev or feature/my-feature.
        def branchNameParts = branchName.split('/')

        int localStart = 0
        if ('origin' == branchNameParts[localStart]) {
            localStart += 1
        }

        def localBranchName = slice(branchNameParts, localStart, -1).join('/')
        scriptObj.logger.info("localBranchName = ${localBranchName}")
        return localBranchName
    }

    static String buildCause(gitlabActionType) {
        return gitlabActionType ?: 'JENKINS_MANUAL'
    }

    /**
     * This method return a list of all the commits that will be included in a merge request
     *
     * @Param scriptObj: Jenkins Declarative Pipeline object
     * @Param separator: Separator to be used between commit ids
     *
     * @Returns List of commit ids for the current branch
     **/
    static String getCommitsList(Script scriptObj, String separator = ',') {
        String branchCommits = getCommits(scriptObj)

        def commits = branchCommits.split('\n').toList()

        // On Windows the 1st line of the return value is the command that was executed.  We have to Strip it
        if (!scriptObj.isUnix()) {
            scriptObj.logger.debug('Windows batch command.  Will have to remove the command line and empty line from retval.')
            commits.removeAt(0) // Remove command line
            scriptObj.logger.debug("After removing command line: ${commits}")
            if (commits.size() > 0) {
                commits.removeAt(0) // Remove command line
                scriptObj.logger.debug("After removing empty line: ${commits}")
            }
        }

        def retval = commits.join(separator)
        scriptObj.logger.info("Commits: ${retval}")
        return retval
    }

    /**
     * This method return all the commits that will be included in a merge request
     *
     * @Param scriptObj: Jenkins Declarative Pipeline object
     *
     * @Returns List of commit ids for the current branch
     **/
    static protected String getCommits(Script scriptObj) {
        def getCommitsCommand = "git log ${scriptObj.env.gitlabTargetBranch}..${scriptObj.env.gitlabSourceBranch} --pretty="
        getCommitsCommand += (scriptObj.isUnix()) ? '%H' : '%%H'

        def branchCommits = ''

        def execScript = scriptObj.&sh
        if (!scriptObj.isUnix()) {
            execScript = scriptObj.&bat
        }

        try {
            scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.gitLabSSHCredentialsId, keyFileVariable: 'keyfile')]) {
                scriptObj.sshagent([scriptObj.pipelineParams.gitLabSSHCredentialsId]) {
                    execScript "git checkout ${scriptObj.env.gitlabTargetBranch}"
                    scriptObj.logger.debug('Target branch status: ') { execScript 'git status' }
                    execScript 'git pull'
                    execScript "git checkout ${scriptObj.env.gitlabSourceBranch}"
                    scriptObj.logger.debug('Source branch status: ') { execScript 'git status' }
                    execScript 'git pull'
                    branchCommits = execScript(script: "${getCommitsCommand}", returnStdout: true, label: 'Querying GitLab about Commits for this MR')
                }
            }
        }
        catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
            scriptObj.error("Unable to find a valid entry in the credential vault with id = ${scriptObj.pipelineParams.gitLabSSHCredentialsId}, error: ${e.message}", e)
        }

        scriptObj.logger.debug('Revision List: \n')
        scriptObj.logger.debug(branchCommits)

        return branchCommits
    }

    private static void getToFileWithSecret(Script scriptObj,
                                            int repo,
                                            String token,
                                            String tokenName,
                                            String repoPath,
                                            String ref,
                                            String filePath) {
        String url = "https://git.platform.manulife.io/\
api/v4/projects/${repo}/repository/files/${repoPath.replace('/', '%2F')}?ref=${ref}"

        String content = null
        boolean gotJson = false
        String body = null
        for (int i = 0; i < MAXTRIES; i++) {
            body = Shell.quickShell(scriptObj,
                    "curl -sS -m ${MAXTIME} -H \"Private-Token: ${token}\" \"${url}\" || :",
                    null, false, true, Level.DEBUG)
            gotJson = (body.take(2) == '{\"')
            /*
            def http = new URL(url).openConnection()
            http.setRequestProperty("Private-Token", token)
            String body = http.inputStream.withReader("UTF-8") { Reader reader ->
                    reader.text
                }
            */

            /*
            def http = new URL(url).openConnection()
            http.setRequestProperty("Private-Token", token)
            int status = http.responseCode
            InputStream is = (status == 200) ? http.inputStream : http.errorStream
            String body = is.getText("UTF-8")
            is.close()
            */

            /*
            def response = scriptObj.httpRequest(url: url, customHeaders: [[name: "Private-Token", value: token, maskValue: true]])
            String body = response.content
            int status = response.status
            */

            // boolean gotJson = (status == 200)

            // JsonSlurper#parseText() returns a non-serializable LazyMap.
            // To avoid returning a non-serializable object, we return a String field.
            if (gotJson) {
                def data = new groovy.json.JsonSlurper().parseText(body)
                if (data.containsKey('content')) {
                    content = data.content
                }
                data = null
            }

            if (content != null) {
                break
            }
        }

        if (content == null) {
            throw new GitLabUtilsException("HTTP response \"${body.take(40)}${body.size() > 40 ? '...' : ''}\" trying \
to get \"${url}\" using a Jenkins credential \"${tokenName}\"")
        }

        scriptObj.writeFile(file: filePath, text: content, encoding: 'Base64')
    }

    static String getToFile(Script scriptObj, int repo, String repoPath, String ref) {
        String tokenName = scriptObj.pipelineParams.gitLabAPITokenName
        String tmpdirShell = scriptObj.pwd(tmp: true).replace('\\', '/')
        String filePath = "${tmpdirShell}/${repoPath.split('/').last()}"
        try {
            scriptObj.logger.debug("Checking a secret text Jenkins credential \"${tokenName}\"...")
            try {
                scriptObj.withCredentials([scriptObj.string(credentialsId: tokenName, variable: 'GITLAB_API_TOKEN_TEXT')]) {
                    getToFileWithSecret(scriptObj, repo, scriptObj.env.GITLAB_API_TOKEN_TEXT, tokenName, repoPath, ref, filePath)
                }
            }
            catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                scriptObj.logger.debug("Checking a username/password Jenkins credential \"${tokenName}\"...")
                try {
                    scriptObj.withCredentials([
                            scriptObj.usernamePassword(credentialsId: tokenName,
                                usernameVariable: 'GITLAB_API_TOKEN_USR', passwordVariable: 'GITLAB_API_TOKEN_PSW')]) {
                        getToFileWithSecret(scriptObj, repo, scriptObj.env.GITLAB_API_TOKEN_PSW, tokenName, repoPath, ref, filePath)
                    }
                }
                catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e2) {
                    throw new GitLabUtilsException("Unable to find either a \"secret text\" or a \"user name/password\" Jenkins \
credential by name \"${tokenName}\", error: ${e2.message}", e2)
                }
            }
        }
        catch (java.io.FileNotFoundException httpErr) {
            throw new GitLabUtilsException("HTTP error ${httpErr.message} downloading \"${repoPath}\" from repo \"${repo}\" ref \"${ref}\"\
with a Gitlab token in \"${tokenName}\"")
        }

        return filePath
    }

    private static String getApiValueWithSecret(Script scriptObj, String token, String tokenName,
            String api, String attr) {
        String url = "https://git.platform.manulife.io/api/v4/${api}"

        String apiValue = null
        String body = Shell.quickShell(scriptObj,
            "curl -sS -m ${MAXTIME} -H \"Private-Token: ${token}\" \"${url}\" || :",
            null, false, true, Level.DEBUG)
        boolean gotJson = (body.take(2) == '{\"')
        if (gotJson) {
            def data = new groovy.json.JsonSlurper().parseText(body)
            if (data.containsKey(attr)) {
                apiValue = "${data[attr]}"
            }

            data = null
        }

        if (apiValue == null) {
            throw new GitLabUtilsException("HTTP response \"${body.take(40)}${body.size() > 40 ? '...' : ''}\" trying \
to get \"${url}\" using a Jenkins credential \"${tokenName}\"")
        }
        return apiValue
    }

    static String getApiValue(Script scriptObj, String api, String attr) {
        String tokenName = scriptObj.pipelineParams.gitLabAPITokenName
        if (tokenName == null) {
            scriptObj.logger.warning('This pipeline did not attempt to load a property gitLabAPITokenName; setting to GitLabApiTokenText for now')
            tokenName = 'GitLabApiTokenText'
        }

        try {
            try {
                scriptObj.withCredentials([scriptObj.string(credentialsId: tokenName, variable: 'GITLAB_API_TOKEN_TEXT')]) {
                    return getApiValueWithSecret(scriptObj, scriptObj.env.GITLAB_API_TOKEN_TEXT, tokenName, api, attr)
                }
            }
            catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                try {
                    scriptObj.withCredentials([
                            scriptObj.usernamePassword(credentialsId: tokenName,
                                usernameVariable: 'GITLAB_API_TOKEN_USR', passwordVariable: 'GITLAB_API_TOKEN_PSW')]) {
                        return getApiValueWithSecret(scriptObj, scriptObj.env.GITLAB_API_TOKEN_PSW, tokenName, api, attr)
                    }
                }
                catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e2) {
                    throw new GitLabUtilsException("Unable to find either a \"secret text\" or a \"user name/password\" Jenkins \
credential by name \"${tokenName}\", error: ${e2.message}")
                }
            }
        }
        catch (java.io.FileNotFoundException httpErr) {
            throw new GitLabUtilsException("HTTP error ${httpErr.message} getting a value of \"${attr}\" in \"${api}\" \
with a Gitlab token in \"${tokenName}\"")
        }
    }


    private static String postStatusWithSecret(Script scriptObj,
                                               int repo,
                                               String token,
                                               String tokenName,
                                               String commitId,
                                               String targetUrl,
                                               String status) {
        String url = "https://git.platform.manulife.io/api/v4/projects/${repo}/statuses/${commitId}"
        String updatedStatus = null
        String body = Shell.quickShell(scriptObj,
            "curl -sS -m ${MAXTIME} -H \"Private-Token: ${token}\" \"${url}\" \
--data-urlencode name=\"Jenkins\" \
--data-urlencode target_url=\"${targetUrl}\" \
--data-urlencode state=\"${status}\" || :",
            null, false, true, Level.DEBUG)

        boolean gotJson = (body.take(2) == '{\"')
        if (gotJson) {
            def data = new groovy.json.JsonSlurper().parseText(body)
            if (data.containsKey('status')) {
                updatedStatus = data.status
            }
            data = null
        }

        if (updatedStatus == null) {
            scriptObj.logger.debug("HTTP response \"${body.take(40)}${body.size() > 40 ? '...' : ''}\" trying \
to POST \"${url}\" using a Jenkins credential \"${tokenName}\"")
        }
        return updatedStatus
    }

    static String postStatus(Script scriptObj, int repo,
            String commitId, String targetUrl, String status) {
        String tokenName = scriptObj.pipelineParams.gitLabAPITokenName
        String updatedStatus = null
        try {
            try {
                scriptObj.withCredentials([scriptObj.string(credentialsId: tokenName, variable: 'GITLAB_API_TOKEN_TEXT')]) {
                    updatedStatus = postStatusWithSecret(scriptObj, repo, scriptObj.env.GITLAB_API_TOKEN_TEXT, tokenName,
                            commitId, targetUrl, status)
                }
            }
            catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                try {
                    scriptObj.withCredentials([
                            scriptObj.usernamePassword(credentialsId: tokenName,
                                usernameVariable: 'GITLAB_API_TOKEN_USR', passwordVariable: 'GITLAB_API_TOKEN_PSW')]) {
                        updatedStatus = postStatusWithSecret(scriptObj, repo, scriptObj.env.GITLAB_API_TOKEN_PSW, tokenName,
                                commitId, targetUrl, status)
                    }
                }
                catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e2) {
                    throw new GitLabUtilsException("Unable to find either a \"secret text\" or a \"user name/password\" Jenkins \
credential by name \"${tokenName}\", error: ${e2.message}")
                }
            }
        }
        catch (java.io.FileNotFoundException httpErr) {
            scriptObj.logger.error("HTTP error ${httpErr.message} setting status \"${status}\" for repo \"${repo}\" commitId \"${commitId}\"\
with a Gitlab token in \"${tokenName}\"")
        }

        return updatedStatus
    }

    static String postStatus(Script scriptObj, String status) {
        if (Boolean.valueOf(scriptObj.pipelineParams.gitLabEnableNotifications)) {
            int projectId = getProjectId(scriptObj)
            if (projectId) {
                return postStatus(scriptObj,
                        projectId,
                        scriptObj.env.GIT_COMMIT,
                        scriptObj.env.BUILD_URL,
                        status)
            }

            scriptObj.logger.debug("Skipping the assignment of status ${status} to Gitlab project ${scriptObj.env.GIT_URL} \
commit ${scriptObj.env.GIT_COMMIT}")
        }
        return null
    }

    static String postStatus(Script scriptObj) {
        String gitLabStatus = pipelineResultToGitLabPipelineStatus(scriptObj)
        return postStatus(scriptObj, gitLabStatus)
    }

    static String pipelineResultToGitLabPipelineStatus(Script scriptObj) {
        switch (scriptObj.currentBuild.currentResult) {
            case 'SUCCESS':
                return 'success'
            case 'UNSTABLE':
                return 'failed'
            case 'ERROR':
                return 'failed'
            case 'ABORTED':
                return 'canceled'
            default:
                return 'failed'
        }
    }

    static int getProjectId(Script scriptObj) {
        if (scriptObj.env.GITLAB_PROJECT_ID != null) {
            return scriptObj.env.GITLAB_PROJECT_ID.toInteger()
        }
        if (scriptObj.env.GIT_URL == null) {
            return 0
        }
        String[] urlPieces = scriptObj.env.GIT_URL.split('/')
        if (urlPieces.size() != 5) {
            return 0
        }
        // ssh://git@git.platform.manulife.io:2222/appsec/ExampleJavaMaven.git
        String group = urlPieces[3]
        String proj = urlPieces[4]
        int extPos = proj.lastIndexOf('.')
        if (extPos >= 0) {
            proj = proj.take(extPos)
        }
        String projectId = getApiValue(scriptObj, "projects/${group}%2F${proj}", 'id')
        if (projectId == null) {
            return 0
        }

        scriptObj.env.GITLAB_PROJECT_ID = projectId
        return projectId.toInteger()
    }
}
