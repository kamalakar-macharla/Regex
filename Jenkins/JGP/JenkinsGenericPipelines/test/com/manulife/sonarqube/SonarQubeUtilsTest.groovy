package com.manulife.sonarqube

import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.versioning.SemVersion
import org.testng.annotations.Test
import org.testng.Assert

class SonarQubeTest {
    static class MockScript extends Script {
        def unix
        def exitCode
        def env = [:]
        def logger = new Logger(this, Level.INFO)

        Object run() {
            println("Running...")
        }

        def echo(String message) {
            println(message)
        }

        def isUnix() {
            return unix
        }

        def ansiColor(String str, Closure cl) {
            cl()
        }
    }

    @Test
    void testGetProjectVersion_SnapShot_HappyPath() {
        Script mockScript = new MockScript()
        SemVersion semVersion = SemVersion.parse(mockScript, '1.2.3-ALPHA')
        String version = SonarQubeUtils.getProjectVersion(semVersion)
        Assert.assertEquals(version, "1.2")
    }

    @Test
    void testGetProjectVersion_NonSnapShot_HappyPath() {
        Script mockScript = new MockScript()
        SemVersion semVersion = SemVersion.parse(mockScript, '1.2.3')
        String version = SonarQubeUtils.getProjectVersion(semVersion)
        Assert.assertEquals(version, "1.2")
    }

    @Test
    void testShouldPerformFullSonarQubeScanning_TRUE_HappyPath() {
        Script mockScript = new MockScript()
        mockScript.env.SONARQUBE_ACTIVE = 'TRUE'
        Assert.assertTrue(SonarQubeUtils.shouldPerformFullSonarQubeScanning(mockScript, "develop"))
    }

    @Test
    void testShouldPerformFullSonarQubeScanning_FALSE_HappyPath() {
        Script mockScript = new MockScript()
        mockScript.env.SONARQUBE_ACTIVE = 'FALSE'
        Assert.assertFalse(SonarQubeUtils.shouldPerformFullSonarQubeScanning(mockScript, "develop"))
    }
}
