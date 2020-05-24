package com.manulife.snyk

import com.manulife.git.GitFlow
import com.manulife.jenkins.JobName
import com.manulife.pipeline.PipelineType

/**
 *
 * This class takes care of executing a call to Snyk to scan a project for open-source governance.
 *
 * It makes the following assumptions about the Jenkins configuration:
 *
 * Environment Variables:
 *   SNYK_ACTIVE: TRUE | FALSE.  Global kill switch on Snyk in case the tool is down.
 *   SNYK_INSTALLATION_LINUX: Name of the Snyk installation to be used as configured in the 'Global Tool Configuration' panel in Jenkins
 *   SNYK_INSTALLATION_WINDOWS: Name of the Snyk installation to be used as configured in the 'Global Tool Configuration' panel in Jenkins
 *
 * BU Jenkins Folder creds vault:
 *   SNYK_TOKEN: Secret text vault entry that contains the Snyk API key for that BU (It's an Organisation in Snyk)
 *   SNYK_ORGANIZATION: Secret text that contains the UUID of that BU Organisation in Snyk
 *
 * Root Jenkins Folder creds vault:
 *   SNYK_ORGANIZATION_<BU>: Secret text that contains the UUID of that BU Organisation in Snyk
 *   Note: Those entries must be in the 'system' vault, not 'global'.  It makes the entries only available to the plugin but not Jenkins jobs
 *
 * Plugin step code: https://github.com/jenkinsci/snyk-security-scanner-plugin/blob/master/src/main/java/io/snyk/jenkins/SnykStepBuilder.java
 *
 **/
class SnykRunner implements Serializable {
    private static final String SNYK_OUTPUT_FILE = 'snyk.out'
    private static final String SNYK_LOG_TEXT_INDICATING_FAILED_GATING = 'severity vulnerable dependency'
    private static final String SNYK_LOG_TEXT_INDICATING_SUCCESSFUL_GATING = 'Result: 0 known issues'

    private final Script scriptObj
    private final PipelineType pipelineType
    private final SnykResult snykResult
    private final String localBranchName

    SnykRunner(Script scriptObj, PipelineType pipelineType, String localBranchName) {
        this.scriptObj = scriptObj
        this.pipelineType = pipelineType
        this.localBranchName = localBranchName

        this.snykResult = new SnykResult()
    }

    static boolean isRequested(Script scriptObj, boolean forceFullScan, def localBranchName) {
        if (scriptObj.env.SNYK_ACTIVE != 'TRUE') {
            scriptObj.logger.warning('Open-Source Governance (Snyk) has been globally disabled and will be skipped. Please check back later.')
            return false
        }

        return true
    }

    def call(def snykExtraParams) {
        JobName jobName = new JobName(scriptObj)
        String snykTokenName = getSnykTokenName(jobName)

        // TODO: Test with Windows.  We may have to use a different snykInstallation name so we may need 2 environment variables.  One for LINUX and one for Windows.
        //        Not sure if this will require support for a different installation in Global Tools or not (see plugin's snykInstallation param)

        // TODO: Test with feature/fix/hotfix branches

        try {
            String tmpDir = scriptObj.pwd(tmp: true)
            String outputFilePathAndName = "${tmpDir}/${SNYK_OUTPUT_FILE}"

            // Note about the monitorProjectOnBuild property: We only want a dashboard in Snyk for develop, release and master branch.
            //  Another option would be to always monitor and then have a batch job that would delete the feature/fix/hotfix branches dashboards from Snyk's portal every 4(?) weeks after creation

            def snykInstallation = (scriptObj.isUnix()) ? scriptObj.env.SNYK_INSTALLATION_LINUX : scriptObj.env.SNYK_INSTALLATION_WINDOWS

            scriptObj.tee(outputFilePathAndName) {
                scriptObj.withCredentials([scriptObj.string(credentialsId: 'SNYK_ORGANIZATION', variable: 'SNYK_ORGANIZATION')]) {
                    scriptObj.snykSecurity(
                        organisation: scriptObj.env.SNYK_ORGANIZATION,
                        projectName: "${jobName.getProjectName()}-${localBranchName}",
                        severity: 'high',
                        failOnIssues: false,
                        snykInstallation: snykInstallation,
                        snykTokenId: "${snykTokenName}",
                        additionalArguments: " ${scriptObj.logger.level.snykLevel} --prune-repeated-subdependencies ${snykExtraParams}",
                        monitorProjectOnBuild: "${shouldMonitor()}")
               }
            }

            String snykLogContent = scriptObj.readFile(outputFilePathAndName)
            scriptObj.logger.debug("Snyk log content: ${snykLogContent}")

            if (snykLogContent.contains(SNYK_LOG_TEXT_INDICATING_FAILED_GATING)) {
                snykResult.message = 'The project FAILED the Open-Source Governance Gate'
            }
            else if (snykLogContent.contains(SNYK_LOG_TEXT_INDICATING_SUCCESSFUL_GATING)) {
                snykResult.governanceGatePassed = true
                snykResult.message = 'The project PASSED the Open-Source Governance Gate!'
            }
            else {
                scriptObj.logger.error("Can't figure out if the project passed/failed Snyk gating.")
                snykResult.message = 'Project status UNKNOWN.  Snyk was unable to successfully scan the project.  Please reach out to your production support team.'
            }
        }
        catch (e) {
            snykResult.message = "Project status UNKNOWN.  Internal issue while calling Snyk scanner: ${e} - ${e.message}.  Please reach out to your production support team."
        }

        // TODO: For merge requests and notes we should upload the results to the MR comments
        // Would just write the following:
        // Snyk scanning result:
        // snykResult.message
        // ----------------------------------------------
        // Content of outputFilePathAndName
    }

    SnykResult getResult() {
        return snykResult
    }

    protected shouldMonitor() {
        GitFlow gitFlow = new GitFlow(scriptObj, scriptObj.pipelineParams.gitFlowType)
        return !gitFlow.isTemporaryBranch(localBranchName)
    }

    private static getSnykTokenName(JobName jobName) {
        return "SNYK_TOKEN_${jobName.getBUName()}".toUpperCase()
    }
}
