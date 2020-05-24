package com.manulife.audittrail

import com.manulife.gitlab.GitLabUtils
import org.codehaus.groovy.runtime.StackTraceUtils
import groovy.json.StringEscapeUtils
import com.manulife.project.ProjectConfigurationService

/**
  *
  * This class is responsible to create an audit trail entry for all Jenkins Generic Pipelines Execution.
  *
  */
class PipelineRunAuditTrailing implements Serializable {
    static final SEGMENTS = ['JH', 'GSPE', 'AA', 'GF', 'GSD']

    static void log(def scriptObj, String targetDeploymentEnvironment = 'N/A') {
        // Examples of build Causes:
        //
        // Running Jenkins job manually:
        // [[_class:hudson.model.Cause$UserIdCause, shortDescription:Started by user Francois Ouellet, userId:frouel1, userName:Francois Ouellet]]
        //
        // When GitLab triggers job on a commit
        // [[_class:com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause, shortDescription:Started by GitLab push by Francois Ouellet]]
        //
        // When triggered by another Jenkins job:
        // [[_class:hudson.model.Cause$UpstreamCause, shortDescription:Started by upstream project "Example_Projects/Test_TriggerJob" build number 1, upstreamBuild:1, upstreamProject:Example_Projects/Test_TriggerJob, upstreamUrl:job/Example_Projects/job/Test_TriggerJob/]]

        try  {

            AuditTrailRecord auditTrailRecord = new AuditTrailRecord()

            auditTrailRecord.buildCauses = scriptObj.currentBuild.buildCauses[0].shortDescription.replaceAll('\"', '')
            auditTrailRecord.number = "${scriptObj.currentBuild.number ?: 'N/A'}"
            auditTrailRecord.result = "${scriptObj.currentBuild.currentResult ?: 'N/A'}"
            auditTrailRecord.shortName = "${scriptObj.currentBuild.projectName ?: 'N/A'}"
            auditTrailRecord.longName = "${scriptObj.currentBuild.fullProjectName ?: 'N/A'}"
            auditTrailRecord.durationInMillis = scriptObj.currentBuild.duration
            auditTrailRecord.durationString = "${scriptObj.currentBuild.durationString ?: 'N/A'}"
            auditTrailRecord.commitId = "${scriptObj.env.GIT_COMMIT ?: 'N/A'}"
            auditTrailRecord.gitLabProjectId = "${GitLabUtils.getProjectId(scriptObj)}"
            auditTrailRecord.gitLabEvent = "${scriptObj.env.gitlabActionType ?: 'manual'}"
            auditTrailRecord.gitLabSSH = "${scriptObj.env.GIT_URL ?: 'N/A'}"

            if (scriptObj.binding.hasVariable('artifact')) {
                auditTrailRecord.artifactInfo = "${scriptObj.artifact.artifactInfo}"
            }
            else {
                auditTrailRecord.artifactInfo = 'N/A'
            }

            if (scriptObj.binding.hasVariable('sonarQubeResult')) {
                def sonarQubeResult = scriptObj.sonarQubeResult
                auditTrailRecord.sonarQubeQualityGateStat = "${String.valueOf(sonarQubeResult.codeQualityGatePassed)}"
                auditTrailRecord.sonarQubeQualityGateStatMsg = "${StringEscapeUtils.escapeJavaScript(sonarQubeResult.message).replaceAll("'", "\\\\'")}"
                auditTrailRecord.sonarQubeBlockerIssueCount = "${sonarQubeResult.sonarBlockerIssueCount}"
                auditTrailRecord.sonarQubeMajorIssueCount = "${sonarQubeResult.sonarMajorIssueCount}"
                auditTrailRecord.sonarQubeCriticalIssueCount = "${sonarQubeResult.sonarCriticalIssueCount}"
            }
            else {
                auditTrailRecord.sonarQubeQualityGateStat = 'N/A'
                auditTrailRecord.sonarQubeQualityGateStatMsg = 'N/A'
                auditTrailRecord.sonarQubeBlockerIssueCount = 'N/A'
                auditTrailRecord.sonarQubeMajorIssueCount = 'N/A'
                auditTrailRecord.sonarQubeCriticalIssueCount = 'N/A'
            }

            if (scriptObj.binding.hasVariable('blackDuckResult')) {
                def blackDuckResult = scriptObj.blackDuckResult
                auditTrailRecord.blackDuckOSGStat = "${String.valueOf(blackDuckResult.governanceGatePassed)}"
                auditTrailRecord.blackDuckOSGStatMsg = "${StringEscapeUtils.escapeJavaScript(blackDuckResult.message).replaceAll("'", "\\\\'")}"
                auditTrailRecord.blackDuckOSGExitCodeType = "${StringEscapeUtils.escapeJavaScript(blackDuckResult.exitCodeType).replaceAll("'", "\\\\'")}"
                auditTrailRecord.blackDuckOSGExitCode = "${blackDuckResult.exitCode}"
            }
            else {
                auditTrailRecord.blackDuckOSGStat = 'N/A'
                auditTrailRecord.blackDuckOSGStatMsg = 'N/A'
            }

            if (scriptObj.binding.hasVariable('snykRunner')) {
                def snykResult = scriptObj.snykRunner.result
                auditTrailRecord.snykStat = "${String.valueOf(snykResult.governanceGatePassed)}"
                auditTrailRecord.snykStatMsg = "${StringEscapeUtils.escapeJavaScript(snykResult.message).replaceAll("'", "\\\\'")}"
            }
            else {
                auditTrailRecord.snykStat = 'N/A'
                auditTrailRecord.snykStatMsg = 'N/A'
            }

            if (scriptObj.binding.hasVariable('fortifyResult')) {
                def fortifyResult = scriptObj.fortifyResult
                auditTrailRecord.fortifyStat = "${String.valueOf(fortifyResult.codeSecurityGatePassed)}"
                auditTrailRecord.fortifyStatMsg = "${StringEscapeUtils.escapeJavaScript(fortifyResult.message).replaceAll("'", "\\\\'")}"
                auditTrailRecord.fortifyIssueCount = "${fortifyResult.fortifyIssueCount}"
            }
            else {
                auditTrailRecord.fortifyStat = 'N/A'
                auditTrailRecord.fortifyStatMsg = 'N/A'
            }

            auditTrailRecord.gatingOverride = (scriptObj.binding.hasVariable('gatingOverride')) ? scriptObj.gatingOverride : false

            // TODO: Should we simply have ALL pipelines setting up that value as part of the initialization phase?
            auditTrailRecord.branch = GitLabUtils.getLocalBranchName(scriptObj)

            // Project name in this format:
            // <Segment>_<BusinessUnit>_RestOfProjectName_<buildEnvironment>_<buildAction>
            String projectName = scriptObj.currentBuild.projectName
            auditTrailRecord.segment = getSegmentName(projectName)
            auditTrailRecord.businessUnit = getBusinessUnitName(projectName)
            auditTrailRecord.buildAction = getBuildAction(projectName)
            auditTrailRecord.buildEnvironment = getBuildEnvironment(projectName)

            def marker = new Throwable()
            String stackTraceLine = StackTraceUtils.sanitize(marker).stackTrace[1]
            auditTrailRecord.pipelineType = "${stackTraceLine[0..stackTraceLine.indexOf('.') - 1]}"
            ProjectConfigurationService project = new ProjectConfigurationService(scriptObj)
            auditTrailRecord.squad = project.getSquadName(auditTrailRecord.gitLabProjectId)

            // TODO: Resolve error value
            auditTrailRecord.errors = 'TODO'
            auditTrailRecord.targetEnvironment = "${targetDeploymentEnvironment}"
            auditTrailRecord.jenkinsNodeName = "${scriptObj.env.NODE_NAME ?: 'N/A'}"

            if (scriptObj.binding.hasVariable('artifact') && auditTrailRecord.pipelineType.contains('Deploy')) {
                def artifactGovernance = scriptObj.artifact

                //Sonarqube
                auditTrailRecord.sonarQubeQualityGateStat = "${String.valueOf(artifactGovernance.sonarQubeScanResult)}"
                auditTrailRecord.sonarQubeQualityGateStatMsg = "${StringEscapeUtils.escapeJavaScript(artifactGovernance.sonarQubeScanMsg).replaceAll("'", "\\\\'")}"

                //Blackduck
                auditTrailRecord.blackDuckOSGStat = "${String.valueOf(artifactGovernance.blackDuckScanResult)}"
                auditTrailRecord.blackDuckOSGStatMsg = "${StringEscapeUtils.escapeJavaScript(artifactGovernance.blackDuckScanMsg).replaceAll("'", "\\\\'")}"

                //Snyk
                auditTrailRecord.snykStat = "${String.valueOf(artifactGovernance.snykScanResult)}"
                auditTrailRecord.snykStatMsg = "${StringEscapeUtils.escapeJavaScript(artifactGovernance.snykScanMsg).replaceAll("'", "\\\\'")}"

                //Fortify
                auditTrailRecord.fortifyStat = "${String.valueOf(artifactGovernance.fortifyScanResult)}"
                auditTrailRecord.fortifyStatMsg = "${StringEscapeUtils.escapeJavaScript(artifactGovernance.fortifyScanMsg).replaceAll("'", "\\\\'")}"
            }

            try {
                auditTrailRecord.stagesExecutionTimeTracker = scriptObj.stagesExecutionTimeTracker
            }
            catch (groovy.lang.MissingPropertyException e) {
                scriptObj.logger.trace('The class doesn\'t have stages execution timings yet.')
            }

            try {
                if (auditTrailRecord.result == 'FAILURE') {
                    auditTrailRecord.failedStage = "${scriptObj.FAILED_STAGE ?: 'N/A'}"
                }
                else {
                    auditTrailRecord.failedStage = ''
                }
            }
            catch (ex) {
                auditTrailRecord.failedStage = 'N/A'
            }

            //TODO: Having permission issues using JsonOutput right now so just manually formating the JSON
            //Example of JSON format: '{"segment": "MFC", "businessUnit": "DIG", "squad": "clarityLIB", "pipelineType": "nodeJS", "buildCauses": "PUSH", "jobShortName": "Something_Cool","jobLongName": "Something_Really_Cool", "jobNumber": 19, "jobResult": "SUCCESS", "error(s)": "", "durationInMillis": 4943, "durationString": "5 mins", "commitId": "fdjkfdl3kjfkjkl34lk", "branch": "dev", "targetEnvironment": "DEV"}'
            //def postdata = JsonOutput.toJson(auditTrailRecord)
            def postdata = "${auditTrailRecord.toString()}"
            scriptObj.logger.debug("Audit Json: ${postdata}")

            def http = new URL('https://clarity-jenkins-api-tst.apps.cac.preview.pcf.manulife.com/v1/audit').openConnection() as HttpURLConnection
            http.setRequestMethod('POST')
            http.setDoOutput(true)
            http.setRequestProperty('Accept', 'application/json')
            http.setRequestProperty('Content-Type', 'application/json')
            http.outputStream.write(postdata.getBytes('UTF-8'))
            http.connect()
            def responseCode = http.responseCode
            http = null
            if (responseCode == 200) {
                scriptObj.logger.info('JGP AuditTrail Submitted')
            }
            else {
                scriptObj.logger.error("[ERROR]: Unable to record pipeline execution audit trail. Audit REST Service returned error: ${responseCode}")
            }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to record pipeline execution audit trail.  Unexpected error(s): ${e.toString()}", e)
        }
    }

    static String getSegmentName(String projectName) {
        int indexFirstUnderscore = projectName.indexOf('_')
        String segment = "${projectName[0..indexFirstUnderscore - 1]}"

        // TODO: When the Canadian Segment includes it's segment name in project name will have to remove this temporary code.
        //  Today, the format for Canadian Segment is "<BusinessUnit>_RestOfProjectName"
        if (!(segment in SEGMENTS)) {
            return 'MFC'
        }

        return segment
    }

    static String getBusinessUnitName(String projectName) {
        int indexFirstUnderscore = projectName.indexOf('_')
        String segment = "${projectName[0..indexFirstUnderscore - 1]}"

        if (!(segment in SEGMENTS)) {
            // TODO: When the Canadian Segment includes it's segment name in project name will have to remove this temporary code.
            //  Today, the format for Canadian Segment is "<BusinessUnit>_RestOfProjectName"
            return segment
        }

        String projectNameWithoutSegment = projectName[indexFirstUnderscore + 1..-1]
        indexFirstUnderscore = projectNameWithoutSegment.indexOf('_')
        return "${projectNameWithoutSegment[0..indexFirstUnderscore - 1]}"
    }

    static String getBuildAction(String projectName) {
        return projectName.tokenize('_').last()
    }

    static String getBuildEnvironment(String projectName) {

        try {
            String[] tokens = projectName.tokenize('_')
            return tokens[tokens.length - 2]
        }
        catch (ex) {
            return 'N/A'
        }
    }
}
