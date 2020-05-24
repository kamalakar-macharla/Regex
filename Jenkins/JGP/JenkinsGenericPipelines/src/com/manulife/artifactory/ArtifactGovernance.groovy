package com.manulife.artifactory

 /**
 * Class to handle artifact gating results, it stores a message and a gating result (true/false) for each artifact
 * Also contains a boolean that will override the gating enforcement when deploying to UAT or PROD, this is a temporary measure that will be removed later
 */
class ArtifactGovernance implements Serializable {
    //Add to audit trail
    //Sonarqube scan result and message
    boolean sonarQubeScanResult = false
    String sonarQubeScanMsg = null

    //Blackduck scan result and message
    boolean blackDuckScanResult = false
    String blackDuckScanMsg = null

    //Fortify scan result and message
    boolean fortifyScanResult = false
    String fortifyScanMsg = null

    //Snyk scan result and message
    boolean snykScanResult = false
    String snykScanMsg = null

    //Deployment override is used to override the deployment of artifacts that failed the quality gates to prod and uat
    //Temporary measure that will be removed later
    boolean deploymentOverride = false

    //Artifact Information for audit JSON
    String artifactInfo = null
}