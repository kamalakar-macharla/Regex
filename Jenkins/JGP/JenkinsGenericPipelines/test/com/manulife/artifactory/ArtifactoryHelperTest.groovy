package com.manulife.artifactory

import com.manulife.artifactory.MockArtifactoryServer
import com.manulife.jenkins.MockScript
import org.testng.annotations.Test
import org.testng.Assert
import com.manulife.logger.Level
import com.manulife.logger.Logger

class ArtifactoryPropertyTest {

    @Test
    void testGetReleaseWriteRepo_withReleaseWriteRepo() {
        def pipelineParams = new Properties()
        pipelineParams.setProperty('releaseWriteRepo', 'releaseWriteRepo')
        pipelineParams.setProperty('releaseRepo', 'releaseRepo')
        String retval = ArtifactoryHelper.getReleaseWriteRepo(pipelineParams)
        Assert.assertEquals(retval, 'releaseWriteRepo')
    }

    @Test
    void testGetReleaseRepo_withoutReleaseWriteRepo() {
        def pipelineParams = new Properties()
        pipelineParams.setProperty('releaseRepo', 'releaseRepo')
        String retval = ArtifactoryHelper.getReleaseWriteRepo(pipelineParams)
        Assert.assertEquals(retval, 'releaseRepo')
    }

    //TODO: Update unit test based on changes to upload method
    //@Test
    void testUpload() {
        def script = new MockScript()
        def server = new MockArtifactoryServer([script: script, credentialsId: "artuser", buildInfo: "test build info"])
        def pipelineParams = new Properties([
            hubVersionDist: "INTERNAL",
            hubVersionPhase: "DEVELOPMENT",
            hubExclusionPattern: "/Nothing/To/Exclude/",
            sonarQubeFailPipelineOnFailedQualityGate: "true",
            hubFailPipelineOnFailedOpenSourceGovernance: "true",
            snykLevel: "true",
            fortifyGating: "true",
        ])
        def ah = new ArtifactoryHelper(script, server)
        ah.uploadArtifact(pipelineParams, "*.nupkg", "mfc-gb-maven-release",
            """true""",
            """false""",
            """false""",
            """The project PASSED the Code Quality Gate!""",
            """Project status UNKNOWN.  BlackDuck wasn't called.""",
            """Project status UNKNOWN.  Snyk wasn't called.""",
            """Project FAILED Code Security Gate.  Fortify detected 110 high or critical issues """ +
                """such as "Cross-Site Scripting: DOM" in dist_index.js:70 (Data Flow)""",
                """10.1""")

        Assert.assertEquals(script.logger.buffer.toString(),        
            """Uploading with Jenkins credential artuser by the spec: {
                "files":
                    [{
                        "pattern": "*.nupkg",
                        "target": "mfc-gb-maven-release",
                        "props": "properties.hubVersionDist=INTERNAL;properties.hubVersionPhase=DEVELOPMENT;properties.hubExclusionPattern=_Nothing_To_Exclude_;gating.CodeQualityGateEnabled=true;gating.OpenSourceGovernanceGateEnabled=true;gating.CodeQualityResult=true;gating.OpenSourceGovernanceResult=false;gating.CodeSecurityResult=false;gating.CodeQualityMsg=The project PASSED the Code Quality Gate!;gating.OpenSourceGovernanceMsg=Project status UNKNOWN.  BlackDuck wasn't called.;gating.CodeSecurityMsg=Project FAILED Code Security Gate.  Fortify detected 110 high or critical issues such as 'Cross-Site Scripting: DOM' in dist_index.js:70 (Data Flow);gating.fortifyGating=true;artifact.version=10.1"
                    }]
            }
properties.hubVersionDist=INTERNAL;properties.hubVersionPhase=DEVELOPMENT;properties.hubExclusionPattern=_Nothing_To_Exclude_;gating.CodeQualityGateEnabled=true;gating.OpenSourceGovernanceGateEnabled=true;gating.CodeQualityResult=true;gating.OpenSourceGovernanceResult=false;gating.CodeSecurityResult=false;gating.CodeQualityMsg=The project PASSED the Code Quality Gate!;gating.OpenSourceGovernanceMsg=Project status UNKNOWN.  BlackDuck wasn't called.;gating.CodeSecurityMsg=Project FAILED Code Security Gate.  Fortify detected 110 high or critical issues such as 'Cross-Site Scripting: DOM' in dist_index.js:70 (Data Flow);gating.fortifyGating=true;artifact.version=10.1
Publishing build info "test build info"
""".toString())                                                     
    }

    @Test
    void testGetDownloadByCommitIdPattern() {
        def expectedValue = """
                {
                    "pattern": "repo/downloadPattern",
                    "props": "vcs.revision=commitId",
                    "flat": "true",
                    "target": "fileName"
                }
        """
        String retval = ArtifactoryHelper.getDownloadByCommitIdPattern('repo', 'downloadPattern', 'commitId', 'fileName')
        Assert.assertEquals(retval, expectedValue)
    }

    @Test
    void testGetSimpleDownloadPattern() {
        def expectedValue = """
                {
                    "pattern": "repo/downloadPattern",
                    "flat": "true",
                    "explode": "false",
                    "target": "target"
                }
        """
        String retval = ArtifactoryHelper.getSimpleDownloadPattern('repo', 'downloadPattern', 'target', (Boolean)null)
        Assert.assertEquals(retval, expectedValue)
    }
}
