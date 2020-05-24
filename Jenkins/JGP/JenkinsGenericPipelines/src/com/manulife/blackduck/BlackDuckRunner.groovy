package com.manulife.blackduck

import com.cloudbees.groovy.cps.NonCPS
import com.manulife.pipeline.PipelineType
import com.manulife.pipeline.PipelineUtils
import com.manulife.util.Conditions
import com.manulife.util.Shell
import com.manulife.util.Strings

/**
 *
 * This class takes care of executing a call to BlackDuck to scan a project for open-source governance.
 *
 */
class BlackDuckRunner implements Serializable {
    final static String CA_BUNDLE_FILE = 'blackduck-zscaler-bundle.pem'

    // https://github.com/blackducksoftware/hub-detect/issues/298
    // Synopsys Case# 00712522: BlackDuck detect Shell script loses multi-word parameters
    final static boolean BLACKDUCK_SHELL_SCRIPT_HAS_EARLY_WORD_EXPANSION = true

    final static String BLACKDUCK_WEB = 'https://detect.synopsys.com/'
    final static String BLACKDUCK_BASENAME = 'detect'
    // "3.3.1" throws an NPE on NuGet projects, "5.4.0" works, "5.5.1" may time out on npm projects
    // The latest (null, currently "5.6.0" is claimed to have fixed the timeout issue).
    final static String BLACKDUCK_VERSION = null

    Script scriptObj
    boolean forceFullScan
    Map pipelineParams
    String localBranchName
    PipelineType pipelineType
    String detectVersion

    // Dependant properties (require calling init())
    Boolean unix
    String tempdir

    BlackDuckRunner(Script scriptObj, boolean forceFullScan, Properties pipelineParams, def localBranchName, PipelineType pipelineType) {
        this.scriptObj = scriptObj
        this.forceFullScan = forceFullScan
        this.pipelineParams = pipelineParams
        this.localBranchName = localBranchName
        this.pipelineType = pipelineType
        this.detectVersion = BLACKDUCK_VERSION
        initFromConstructor()
    }

    /**
      * Work around the disappearance of the default constructor after adding
      * own constructor.
      * <p>
      * Groovy uses the default or no-arg constructor when seeing a call to a
      * non-existing Map constructor.
      * <p>
      *     http://groovy-lang.org/objectorientation.html#_named_parameters
      * <pre>
      *     a = new A()                 // call no-arg constructor
      *     loop over all named props   // set all named arguments
      *         a[name] = value
      * </pre>
      *     Roshan Dawrani, Feb 24, 2010 http://groovy.329449.n5.nabble.com/Default-constructors-td372478.html
      * <p>
      * However, we comment the no-arg constructor out and provide own Map
      * constructor because we want to initialize the dependent properties after
      * Groovy sets the original properties in the caller.
      * <pre>
      *     BlackDuckRunner() {
      *     }
      * </pre>
      * Avoid calling CPS-transformed code (pipeline steps) from constructors
      * which are not-CPS-transformed,
      * <p>
      *     https://issues.jenkins-ci.org/browse/JENKINS-26313
      * <p>
      * Otherwise, the pipeline interpreter throws an exception,
      * <pre>
      *     hudson.remoting.ProxyException: com.cloudbees.groovy.cps.impl.CpsCallableInvocation
      * </pre>
      */
    BlackDuckRunner(Map map) {
        // Scott Davis, Feb 25, 2010 http://groovy.329449.n5.nabble.com/Default-constructors-td372478.html
        map.each { k, v ->
            if (this.hasProperty(k)) {
                this[k] = v
            }
        }
        initFromConstructor()
    }

    /**
      * Initialize dependent properties.
      *
      * Due to the limits of the Jenkins pipeline interpreter's
      * Continuation-Passing Style, this method, when invoked from a
      * constructor, needs to be non-CPS. This, in turn, requires that it does
      * not call CPS code such as pipeline steps.
      */
    @NonCPS
    private void initFromConstructor() {
        // Nope: this.tempdir = scriptObj.pwd(tmp: true).replace('\\', '/')
        // Nope: scriptObj.logger.debug("forceFullScan ${forceFullScan}, tempdir ${tempdir}")
    }

    void init() {
        this.unix = scriptObj.isUnix()
        this.tempdir = scriptObj.pwd(tmp: true).replace('\\', '/')
    }

    static boolean isRequested(Script scriptObj, boolean forceFullScan, def hubTriggers, def localBranchName) {

        if (scriptObj.env.BLACKDUCK_ACTIVE != 'TRUE') {
            scriptObj.logger.warning('BLACKDUCK has been globally disabled and will skip the scan. Please check back later.')
            return false
        }

        if (forceFullScan) {
            return true
        }

        return Conditions.isToolAllowed(scriptObj, 'hub', hubTriggers, localBranchName)
    }

    def callBlackDuck(def blackDuckExtraParams) {
        init()
        BlackDuckResult blackDuckResult = new BlackDuckResult()
        blackDuckResult.message = 'The project wasn\'t scanned with BlackDuck.'
        blackDuckResult.exitCodeType = 'The project wasn\'t scanned with BlackDuck.'
        blackDuckResult.governanceGatePassed = false

        try {
            scriptObj.logger.warning('Please be patient, the BlackDuck scan may take a long time...')
            deleteOldReportsFromWorkspace(unix)

            def blackDuckParams = buildCallParametersString(blackDuckExtraParams)
            // Call BlackDuck Scanner
            def script = getScript(blackDuckParams)
            scriptObj.logger.debug("BlackDuck Script: ${script}")

            def status = executeScript(script)

            if (status == null) {
                scriptObj.logger.error('BlackDuck call result status == null.  Defaulting to -1')
                status = ExitCodeType.UNEXPECTED.exitCode
            }

            def statusObj = ExitCodeType.lookup(status)
            def statusName = statusObj ? statusObj.name() : 'unexpected'
            scriptObj.logger.debug("BlackDuck completed with exit code ${status} (${statusName})")

            blackDuckResult.exitCodeType = "${statusName}"
            blackDuckResult.exitCode = getErrorType("${status}")

            // Handle scanner results
            blackDuckResult.message = statusObj ? statusObj.descr : 'unexpected error'

            if (statusObj == ExitCodeType.SUCCESS) {
                blackDuckResult.governanceGatePassed = true
                blackDuckResult.exitCode = 0
            }
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException | hudson.AbortException e) {
            scriptObj.logger.warning(e.toString() + ';' + e.getMessage() + ';' + e.getStackTrace())
            scriptObj.logger.warning('BlackDuck execution was aborted by a job timeout'
                    + ' in the Jenkinsfile script or by a user.')
            blackDuckResult.message = ('Project status UNKNOWN.  BlackDuck was unable to assess'
                    + ' if the project is compliant with the Open Source Governance because'
                    + ' the execution was aborted by a job timeout in the Jenkinsfile script'
                    + ' or by a user.')

            blackDuckResult.exitCodeType = 'JENKINS_TIMEOUT'
            blackDuckResult.exitCode = '400'
            // Swallow the abort exception, assuming it is sent to other threads as well.
        }
        // Propagate unexpected exceptions, assuming that they are rare and that the JGP
        // developers may want to investigate the stack trace.


        return blackDuckResult
    }

    def deleteOldReportsFromWorkspace(boolean unix) {
        try {
            if (unix) {
                scriptObj.sh 'rm -f *BlackDuck_RiskReport.pdf'
            }
            else {
                scriptObj.bat 'del /f *BlackDuck_RiskReport.pdf'
            }
        }
        catch (err) {
            scriptObj.logger.debug('Could not delete any file matching the *BlackDuck_RiskReport.pdf pattern')
        }
    }

    private getScript(def blackDuckParams) {
        if (unix) {
            String blackDuckParamsForShell = Strings.continueMultiLineForShell(blackDuckParams)
            if (BLACKDUCK_SHELL_SCRIPT_HAS_EARLY_WORD_EXPANSION) {
                blackDuckParamsForShell = Strings.solidifyParams(blackDuckParamsForShell)
            }

            String requireVersion = ''
            if (detectVersion) {
                requireVersion = "export DETECT_LATEST_RELEASE_VERSION=\"${detectVersion}\""
            }

            return Strings.trimAndShift("""
                    #!/bin/bash
                    set -x
                    export DETECT_CURL_OPTS="--cacert ${CA_BUNDLE_FILE}"
                    ${requireVersion}
                    export DETECT_JAR_PATH="${tempdir}"
                    code=\$(curl -sS --write-out "%{http_code}" -o ${BLACKDUCK_BASENAME}.sh \${DETECT_CURL_OPTS} "${BLACKDUCK_WEB}${BLACKDUCK_BASENAME}.sh")
                    [[ "\${code}" == "200" ]]
                    source ${BLACKDUCK_BASENAME}.sh""") + ' ' + blackDuckParamsForShell
        }

        String blackDuckParamsForPowerShell = Strings.continueMultiLineForPowerShell(blackDuckParams)

        // The return value of the Detect function needs passing as its exit
        // code.  Previously, the function would execute "exit" with a code,
        // but a change in the default value of an environment variable changed
        // the "exit" code path to a "return" one.
        //
        // PowerShell functions accumulate a "capture" of all their echo
        // statements that are returned along with the "return" value, so the
        // function and its called code needs to avoid any echo statements in
        // order to return just the "return" value.
        //
        // https://stacktoheap.com/blog/2013/06/15/things-that-trip-newbies-in-powershell-pipeline-output/
        String requireVersion = ''
        if (detectVersion) {
            requireVersion = "\$env:DETECT_LATEST_RELEASE_VERSION = '${detectVersion}'"
        }
        String psscript = """
                \$VerbosePreference = 'Continue'
                \$DebugPreference = 'Continue'
                ${requireVersion}
                \$env:DETECT_JAR_PATH = '${tempdir.replace('/', '\\')}'
                [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
                irm ${BLACKDUCK_WEB}${BLACKDUCK_BASENAME}.ps1?\$(Get-Random) | iex
                exit detect """ + blackDuckParamsForPowerShell

        // Prepare a bat invokation to show progress (invoking as powershell shows no progress).
        return ('powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command '
                    + Strings.escapeMultiLinePowerShellForCmdBatchWithoutQuotes(psscript))
    }

    private executeScript(def script) {
        def retval

        // TODO: consider unifying detect.sh and detect.ps1 with regards to
        // trusting the same CA bundle when downloading things.  Perhaps this
        // can reuse the Java CA store created early in each pipeline with a
        // call to Shell.trustZscalerInJava().  The curl tool would need
        // converting the injected JKS store into a PEM one.
        //
        // For now, only the Shell ("unix") script refers to the CA bundle in
        // downloading with curl.  The PowerShell script relies on the bundle
        // in the Windows registry which is supposedly injected by a group
        // policy with the advent of Zscaler.
        if (unix) {
            scriptObj.logger.debug("Writing to ${scriptObj.env.WORKSPACE}/${CA_BUNDLE_FILE}...")
            String caBundle = scriptObj.libraryResource('com/manulife/ssl/zscaler-curl-bundle.pem')
            scriptObj.writeFile(file: "${scriptObj.env.WORKSPACE}/${CA_BUNDLE_FILE}", text: caBundle, encoding: 'UTF-8')
        }

        if (unix) {
            scriptObj.logger.debug('Shell Type:') { scriptObj.sh('echo \$SHELL') }
            scriptObj.sh("rm -f \"${tempdir}\"/*.jar")
            retval = scriptObj.sh(returnStatus: true, script: script)
        }
        else {
            scriptObj.bat("del /q /f \"${tempdir.replace('/', '\\')}\"\\*.jar")
            retval = scriptObj.bat(returnStatus: true, script: script)
        }

        scriptObj.logger.info("Script returned the following value: ${retval}")

        return retval
    }

    private buildCallParametersString(String blackDuckExtraParams) {
        def blackDuckParams = '''
                '--blackduck.url=https://manulifefinancial.blackducksoftware.com/'
            '''

        if (scriptObj.env.BLACKDUCK_PSW == null) {
            blackDuckParams += """
                    "--blackduck.api.token=${scriptObj.env.BLACKDUCK}" """
        }
        else {
            blackDuckParams += """
                    "--blackduck.username=${scriptObj.env.BLACKDUCK_USR}"
                    "--blackduck.password=${scriptObj.env.BLACKDUCK_PSW}" """
        }

        // When a projet uses a package manager (like Maven, Nuget or Go dep) then we can skip the signature scanner.
        // By default the signature scanner is enabled.
        if (pipelineType == PipelineType.DOTNET ||
            pipelineType == PipelineType.DOTNETCORE ||
            pipelineType == PipelineType.JAVA_MAVEN ||
            pipelineType == PipelineType.AEM_MAVEN ||
            pipelineType == PipelineType.NODEJS ||
            pipelineType == PipelineType.SWIFT ||
            pipelineType == PipelineType.GO) {
            blackDuckParams += '\"--detect.tools=DETECTOR\"'
        }
        else {
            blackDuckParams += '\"--detect.tools=ALL\"'
        }

        def projectName = Strings.canonicalizeAppKey(PipelineUtils.getJobName(scriptObj))

        def exclpat = pipelineParams.hubExclusionPattern
        exclpat += (exclpat ? ',' : '') + '.sonar,.sonarqube,node_modules'

        // unmap=true should override previous scan results, according to a Github issue,
        // https://github.com/blackducksoftware/synopsys-detect/issues/66
        blackDuckParams += """
                "--logging.level.com.synopsys.integration=${scriptObj.logger.level.hubDetectLevel}"
                "--detect.project.name=${projectName}"
                "--detect.project.version.name=${localBranchName}"
                "--detect.project.version.phase=${pipelineParams.hubVersionPhase}"
                "--detect.project.version.distribution=${pipelineParams.hubVersionDist}"
                "--detect.code.location.name=${projectName}-${localBranchName}"
                "--detect.report.timeout=${pipelineParams.hubTimeoutMinutes.toInteger() * 60}"
                "--detect.detector.search.depth=9999"
                "--detect.detector.search.continue=true"
                "--detect.detector.search.exclusion=${exclpat}"
                "--detect.project.codelocation.unmap=true"
                "--detect.source.path=."
                "--detect.blackduck.signature.scanner.exclusion.name.patterns=${exclpat}"
                "--detect.wait.for.results=true"
                "--detect.policy.check.fail.on.severities=${pipelineParams.hubFailOnSeverities}"
                "--detect.risk.report.pdf=true"
                "--detect.risk.report.pdf.path=${scriptObj.env.WORKSPACE}"
            """ + blackDuckExtraParams

        if (pipelineType == PipelineType.NODEJS) {
           blackDuckParams += ' \"--detect.npm.include.dev.dependencies=false\"'
        }

        if (localBranchName.matches('(feature|fix).*') || 'MERGE' == scriptObj.env?.gitlabActionType || 'NOTE' == scriptObj.env?.gitlabActionType) {
            blackDuckParams += ' \"--detect.blackduck.signature.scanner.dry.run=true\"'
        }

        scriptObj.logger.debug("blackDuckParams = ${blackDuckParams}")

        return blackDuckParams
    }

    private int getErrorType(String errorNum) {
        if (errorNum == ExitCodeType.SUCCESS.exitCode) {
            return 200
        }
        else if (errorNum == ExitCodeType.FAILURE_POLICY_VIOLATION.exitCode || errorNum == ExitCodeType.FAILURE_DETECTOR.exitCode || errorNum == ExitCodeType.FAILURE_CONFIGURATION.exitCode) {
            return 400
        }
        else {
            return 500
        }
    }
}
