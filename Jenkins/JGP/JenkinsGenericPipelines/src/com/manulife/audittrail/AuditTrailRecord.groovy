package com.manulife.audittrail

/**
  *
  * This class represents one pipeline execution audit trail entry.
  *
  */
class AuditTrailRecord implements Serializable {
    String segment           // Manulife Segment for which this pipeline is
    String businessUnit      // Business unit this pipeline is for
    String squad             // Squad this pipeline belongs to
    String pipelineType      // JGP pipeline type
    String buildCauses       // What triggered the job?  Manual Trigger, another job, a GitLab WebHook?
    String buildAction       // CI / CD / Promotion
    String buildEnvironment  // DEV, RELEASE, PRODUCTION
    String shortName         // Jenkins job short name
    String longName          // Jenkins job long name
    String number            // Jenkins job number
    String result            // SUCCESSFUL, UNSTABLE, FAILED
    String errors            // Error(s) generated during pipeline execution
    long durationInMillis    // Job execution duration in milliseconds
    String durationString    // Job execution duration in human readable format
    String commitId          // GitLab code change set
    String gitLabProjectId   // Repos unique project id
    String gitLabEvent       // PUSH, MR, NOTES
    String gitLabSSH         // GitLab repo SSH URI location
    String branch            // GitLab repo branch name
    String targetEnvironment // Environment into which a deployment occurs
    String sonarQubeQualityGateStat // SonarQube Code Quality gate status
    String blackDuckOSGStat  // Black Duck OpenSourceGovernance status
    String snykStat  // Snyk OpenSourceGovernance status
    String fortifyStat       // Fortify Code Security Gate status
    String sonarQubeQualityGateStatMsg // SonarQube Code Quality gate message
    String blackDuckOSGStatMsg // Black Duck OpenSourceGovernance message
    String snykStatMsg // Snyk OpenSourceGovernance message
    String fortifyStatMsg    // Fortify Code Security Gate message
    String fortifyIssueCount // Fortify issues produced
    String gatingOverride    //Deployment pipeline gating override
    String failedStage      // CI stage names information
    String sonarQubeBlockerIssueCount // SonarQube blocker issue count
    String sonarQubeMajorIssueCount // SonarQube major issue count
    String sonarQubeCriticalIssueCount // SonarQube critical issue count
    String blackDuckOSGExitCodeType //Black Duck OpenSourceGovernance error code type
    String blackDuckOSGExitCode //Black Duck OpenSourceGovernance error code
    long initStageDuration
    long increasePatchVersionStageDuration
    long resolveDependenciesAndBuildStageDuration
    long runUnitTestsStageDuration
    long codeReviewStageDuration
    long openSourceGovernanceStageDuration
    long securityCodeScanningStageDuration
    long packageAndStoreStageDuration
    long downloadBinaryStageDuration
    long prepareRequestStageDuration
    long manageServiceDependenciesStageDuration
    long submitRequestApiStageDuration
    long monitorProgressStageDuration
    long logBinaryStatusStageStart
    String jenkinsNodeName
    String artifactInfo

    String toString() {
        return "{\"segment\": \"${segment}\", " +
               "\"businessUnit\": \"${businessUnit}\", " +
               "\"squad\": \"${squad}\", " +
               "\"pipelineType\": \"${pipelineType}\", " +
               "\"buildCauses\": \"${buildCauses}\", " +
               "\"buildAction\": \"${buildAction}\", " +
               "\"buildEnvironment\": \"${buildEnvironment}\", " +
               "\"jobShortName\": \"${shortName}\", " +
               "\"jobLongName\": \"${longName}\", " +
               "\"jobNumber\": \"${number}\", " +
               "\"jobResult\": \"${result}\", " +
               "\"error(s)\": \"${errors}\", " +
               "\"durationInMillis\": ${durationInMillis}, " +
               "\"durationString\": \"${durationString}\", " +
               "\"commitId\": \"${commitId}\", " +
               "\"gitLabProjectId\": \"${gitLabProjectId}\", " +
               "\"gitLabEvent\": \"${gitLabEvent}\", " +
               "\"gitLabSSH\": \"${gitLabSSH}\", " +
               "\"branch\": \"${branch}\", " +
               "\"sonarQubeQualityGateStat\": \"${sonarQubeQualityGateStat}\", " +
               "\"blackDuckOSGStat\": \"${blackDuckOSGStat}\", " +
               "\"snykStat\": \"${snykStat}\", " +
               "\"fortifyStat\": \"${fortifyStat}\", " +
               "\"fortifyIssueCount\": \"${fortifyIssueCount}\", " +
               "\"sonarQubeQualityGateStatMsg\": \"${sonarQubeQualityGateStatMsg}\", " +
               "\"sonarQubeBlockerIssueCount\": \"${sonarQubeBlockerIssueCount}\", " +
               "\"sonarQubeMajorIssueCount\": \"${sonarQubeMajorIssueCount}\", " +
               "\"sonarQubeCriticalIssueCount\": \"${sonarQubeCriticalIssueCount}\", " +
               "\"blackDuckOSGStatMsg\": \"${blackDuckOSGStatMsg}\", " +
               "\"blackDuckOSGExitCodeType\": \"${blackDuckOSGExitCodeType}\", " +
               "\"blackDuckOSGExitCode\": \"${blackDuckOSGExitCode}\", " +
               "\"snykStatMsg\": \"${snykStatMsg}\", " +
               "\"fortifyStatMsg\": \"${fortifyStatMsg}\", " +
               "\"gatingOverride\": \"${gatingOverride}\", " +
               "\"failedStage\": \"${failedStage}\", " +
               "\"targetEnvironment\": \"${targetEnvironment}\", " +
               "\"initStageDuration\": \"${initStageDuration}\", " +
               "\"increasePatchVersionStageDuration\": ${increasePatchVersionStageDuration}, " +
               "\"resolveDependenciesAndBuildStageDuration\": ${resolveDependenciesAndBuildStageDuration}, " +
               "\"runUnitTestsStageDuration\": ${runUnitTestsStageDuration}, " +
               "\"codeReviewStageDuration\": ${codeReviewStageDuration}, " +
               "\"openSourceGovernanceStageDuration\": ${openSourceGovernanceStageDuration}, " +
               "\"securityCodeScanningStageDuration\": ${securityCodeScanningStageDuration}, " +
               "\"packageAndStoreStageDuration\": ${packageAndStoreStageDuration}, " +
               "\"downloadBinaryStageDuration\": ${downloadBinaryStageDuration}, " +
               "\"prepareRequestStageDuration\": ${prepareRequestStageDuration}, " +
               "\"manageServiceDependenciesStageDuration\": ${manageServiceDependenciesStageDuration}, " +
               "\"submitRequestApiStageDuration\": ${submitRequestApiStageDuration}, " +
               "\"monitorProgressStageDuration\": ${monitorProgressStageDuration}, " +
               "\"logBinaryStatusStageStart\": ${logBinaryStatusStageStart}, " +
               "\"artifactInfo\": \"${artifactInfo}\", " +
               "\"jenkinsNodeName\": \"${jenkinsNodeName}\"}"
    }

    def setStagesExecutionTimeTracker(StagesExecutionTimeTracker timeTracker) {
        initStageDuration = timeTracker.initStageDuration()
        increasePatchVersionStageDuration = timeTracker.increasePatchVersionStageDuration()
        resolveDependenciesAndBuildStageDuration = timeTracker.resolveDependenciesAndBuildStageDuration()
        runUnitTestsStageDuration = timeTracker.runUnitTestsStageDuration()
        codeReviewStageDuration = timeTracker.codeReviewStageDuration()
        openSourceGovernanceStageDuration = timeTracker.openSourceGovernanceStageDuration()
        securityCodeScanningStageDuration = timeTracker.securityCodeScanningStageDuration()
        packageAndStoreStageDuration = timeTracker.packageAndStoreStageDuration()
        downloadBinaryStageDuration = timeTracker.downloadBinaryStageDuration()
        prepareRequestStageDuration = timeTracker.prepareRequestStageDuration()
        manageServiceDependenciesStageDuration = timeTracker.manageServiceDependenciesStageDuration()
        submitRequestApiStageDuration = timeTracker.submitRequestApiStageDuration()
        monitorProgressStageDuration = timeTracker.monitorProgressStageDuration()
        logBinaryStatusStageStart = timeTracker.logBinaryStatusStageDuration()
    }
}
