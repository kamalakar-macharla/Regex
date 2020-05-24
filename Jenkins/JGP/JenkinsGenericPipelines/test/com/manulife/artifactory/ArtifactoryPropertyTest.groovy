package com.manulife.artifactory

import com.manulife.artifactory.MockArtifactoryServer
import com.manulife.jenkins.MockScript
import org.testng.annotations.Test
import org.testng.Assert

class ArtifactoryHelperTest {
    @Test
    void testFixValue() {
        Assert.assertEquals(ArtifactoryProperty.fixValue("""Project FAILED Code Security Gate.  Fortify detected 110 high or critical issues such as "Cross-Site Scripting: DOM" in dist_index.js:70 (Data Flow)"""),
            """Project FAILED Code Security Gate.  Fortify detected 110 high or critical issues such as 'Cross-Site Scripting: DOM' in dist_index.js:70 (Data Flow)""")
    }
}
