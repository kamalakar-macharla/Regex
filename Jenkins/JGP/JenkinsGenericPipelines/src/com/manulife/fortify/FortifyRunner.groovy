package com.manulife.fortify

// vim: et:ts=4:sts=4:sw=4:fileencoding=utf-8

import com.manulife.logger.Level
import com.manulife.util.Conditions
import com.manulife.util.Shell
import com.manulife.util.Strings
import com.manulife.gitlab.GitLabUtils

/**
 *
 * This class is responsible to call the Fortify code security scanner.
 *
 **/
class FortifyRunner implements Serializable {

    Script scriptObj
    String localBranchName
    Properties pipelineParams
    String buildId
    String opts

    def fortifyApp
    def fortifyVer
    def fortifyAppDescr
    def fortifySSC
    def jobBaseName
    def scriptWeb
    def unix
    def fortifyRoot

    static String obtainFortifyRoot(Script scriptObj, boolean unix) {
        // TODO: We should be using environment variables instead.  This won't work on Docker...
        return unix ? "${scriptObj.env.HOME}/Home/workspace/fortify" : 'e:/fortify'
    }

    def init() {
        if (!scriptObj || !localBranchName || !pipelineParams) {
            scriptObj.error('FortifyRunner requires named arguments scriptObj, localBranchName and pipelineParams in its constructor')
        }
        else {
            jobBaseName = scriptObj.env.JOB_BASE_NAME

            fortifyApp = (pipelineParams.fortifyApp ?: pipelineParams.projectName ?: pipelineParams.sonarQubeProjectKey ?: jobBaseName)

            fortifyVer = pipelineParams.fortifyVer ?: localBranchName

            unix = scriptObj.isUnix()
            fortifyRoot = obtainFortifyRoot(scriptObj, unix)

            fortifyAppDescr = pipelineParams.fortifyAppDescr ?: Shell.quickShell(scriptObj,
                'git config remote.origin.url', unix, false, false, Level.DEBUG).trim()

            fortifySSC = pipelineParams.fortifyServer
            scriptWeb = pipelineParams.fortifyScriptWeb
        }
    }

    def translateOnly(def opts = null) {
        def scriptResult = runScript('-t' + (opts ? (' ' + opts) : ''))
        if (scriptResult) {
            throw new FortifyRunnerException(scriptResult)
        }
    }

    def run(def opts = null) {
        FortifyResult fortifyResult = new FortifyResult()
        fortifyResult.message = "The project wasn't scanned with Fortify."
        fortifyResult.codeSecurityGatePassed = false

        try {
            def scriptResult = runScript(opts)
            validateCodeSecurityGate(scriptResult, true, fortifyResult)
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException | hudson.AbortException e) {
            scriptObj.logger.warning('Fortify execution was aborted by a job timeout'
                    + ' in the \"Jenkinsfile\" script or by a user.')
            fortifyResult.message = ('Project status UNKNOWN.  Fortify was unable to assess'
                    + ' if the project is compliant with Code Security Governance'
                    + ' because its execution was aborted by a job timeout'
                    + ' in the \"Jenkinsfile\" script or by a user.')
            // Swallow the abort exception, assuming it is sent to other threads as well.
        }
        // Propagate unexpected exceptions, assuming that they are rare and that the JGP
        // developers may want to investigate the stack trace.

        return fortifyResult
    }

    protected runScript(def runOpts = null) {
        // Avoid a silent exit on calling global steps such as echo (println),
        // sh, bat and error, by resolving them against the script object.
        // https://stackoverflow.com/questions/42149652/println-in-call-method-of-vars-foo-groovy-works-but-not-in-method-in-class
        scriptObj.logger.warning('Please wait, the Fortify scan may take between 10 minutes and 2 hours (60 times the build time)...')

        // Re-evaluate isUnix() in case the build changed its node
        unix = scriptObj.isUnix()
        fortifyRoot = obtainFortifyRoot(scriptObj, unix)

        def fortifyOpts = ''
        if (buildId != null) {
            fortifyOpts += " -b \"${buildId}\""
        }

        if (opts != null) {
            fortifyOpts += " ${opts}"
        }

        if (runOpts != null) {
            fortifyOpts += " ${runOpts}"
        }

        String fortifyWrapperRepoStr, fortifyWrapperRef, repoDir
        (fortifyWrapperRepoStr, fortifyWrapperRef, repoDir) = scriptWeb.split(',')
        int fortifyWrapperRepo = fortifyWrapperRepoStr.toInteger()
        String fortifyWrapperPath = GitLabUtils.getToFile(scriptObj, fortifyWrapperRepo, "${repoDir}fortify.sh", fortifyWrapperRef)
        GitLabUtils.getToFile(scriptObj, fortifyWrapperRepo, "${repoDir}fortify-ssc.py", fortifyWrapperRef)
        def shellScript = """source \"${fortifyWrapperPath}\" \\
                    "${fortifySSC}" \\
                    "${fortifyApp}" "${fortifyVer}" "${fortifyAppDescr}" \\
                    "${fortifyRoot}"${fortifyOpts}
            """

        // Call Fortify
        def platformScript
        int status
        def scriptResult = ''

        if (unix) {
            platformScript = Strings.trimAndShift('''export PATH="/bin:/usr/bin"
                ''' + shellScript)
            status = scriptObj.sh(returnStatus: true, script: platformScript)
        }
        else {
            // Protect line breaks and special characters such as CMD pipe | against the greedy interpretation by CMD.
            //
            // Avoid wrapping the CMD script with double quotes to satisfy
            // CMD's failure to follow a quoted string across multiple lines.
            //
            // Use quoted strings for uniform escape rules (applied once
            // against Groovy interpolation and another against the RegEx
            // interpreter).  This seems easier than slashy Groovy strings that
            // do not seem to allow visible representation of newlines.
            platformScript = "\"%cygbinslash%bash.exe\" -exc '" +
                    Strings.escapeMultiLineShellForCmdBatchInSingleQuotes("""windir="\$(/usr/bin/cygpath -u "\${WINDIR}")"
                            export PATH="/bin:/usr/bin:\${windir}/System32:\${windir}/System32/WindowsPowerShell/v1.0"
                        """ + shellScript) + '\''

            status = scriptObj.bat(returnStatus: true, script: platformScript)
        }

        if (status) {
            scriptResult = "Exit code ${status} executing the following script.\n${platformScript}"

            if (scriptObj.env.GITLAB_API_TOKEN_PSW != null) {
                scriptResult = scriptResult.replace(scriptObj.env.GITLAB_API_TOKEN_PSW, '****')
            }
        }

        return scriptResult
    }

    private validateCodeSecurityGate(scriptResult, checkResultsFile, fortifyResult) {
        if (scriptResult) {
            fortifyResult.message = scriptResult
            fortifyResult.codeSecurityGatePassed = false
            fortifyResult.fortifyIssueCount = 0
        }
        else if (checkResultsFile) {
            int numHighs = 0
            String firstCategory = null
            String firstIssue = null
            String issuesOutput = scriptObj.readFile("${scriptObj.env.WORKSPACE}/fortify-issues.txt")
            def issuesOutputLines = issuesOutput.split('\n')

            for (def line in issuesOutputLines) {
                line = line.trim()

                if (!line) {
                    continue
                }

                def m = Strings.match(line, /(\d+) issues of (\d+) matched search query.*/)

                if (m) {
                    numHighs = m[0][1].toInteger()
                }
                else if (line.startsWith('Issue counts')) {
                    // Nothing to do
                }
                else if (!firstCategory) {
                    firstCategory = line
                    def c = Strings.match(firstCategory, /"([^\"]+)".*/)
                    if (c) {
                        firstCategory = c[0][1]
                    }
                }
                else {
                    firstIssue = line
                    break
                }
            }
            if (numHighs > 0) {
                fortifyResult.message = "Project FAILED Code Security Gate.  Fortify detected ${numHighs} high or critical issues such as \"${firstCategory}\" in ${firstIssue}"
                fortifyResult.codeSecurityGatePassed = false
                fortifyResult.fortifyIssueCount = numHighs
            }
            else {
                fortifyResult.message = 'Project PASSED Code Security Gate!'
                fortifyResult.codeSecurityGatePassed = true
                fortifyResult.fortifyIssueCount = numHighs
            }
        }
        else {
            // Keep the original "the project wasn't scanned" / false result.
        }
    }

    static boolean isRequested(def scriptObj, def forceFullScan, def fortifyTriggers, def localBranchName) {

        if (scriptObj.env.FORTIFY_ACTIVE != 'TRUE') {
            scriptObj.logger.warning('FORTIFY has been globally disabled and will skip the scan. Please check back later')
            return false
        }

        if (forceFullScan) {
            return true
        }

        return Conditions.isToolAllowed(scriptObj, 'fortify', fortifyTriggers, localBranchName)
    }
}

