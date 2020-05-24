package com.manulife.snyk

import com.manulife.git.GitFlowType
import com.manulife.jenkins.JobName
import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.pipeline.PipelineType


import org.testng.annotations.Test
import org.testng.Assert

class SnykRunnerTest {
    class MockScript extends Script {
        def env = [:]
        def logger = new Logger(this, Level.DEBUG)
        def printed
        def pipelineParams = [:]

        MockScript(String scripName, GitFlowType gitFlowType = GitFlowType.GITFLOW) {
            this.JOB_NAME = scripName
            this.pipelineParams.gitFlowType = gitFlowType
        }

        Object run() {
            println("Running...")
        }

        def ansiColor(String str, Closure cl) {
            cl()
        }

        def echo(String message) {
            printed = message
        }
    }

    @Test
    void test_Constructor_HappyPath() {
        MockScript mockScript = new MockScript('Example_MyProject_DEV_CI')
        SnykRunner testee = new SnykRunner(mockScript, PipelineType.DOTNET, 'develop')
        Assert.assertEquals(testee.result.message, "Project status UNKNOWN.  Snyk wasn't called.")
        Assert.assertEquals(testee.result.governanceGatePassed, false)
    }

    @Test
    void test_isRequested_SnykActive() {
        MockScript mockScript = new MockScript('Example_MyProject_DEV_CI')
        mockScript.env.SNYK_ACTIVE = 'TRUE'
        Assert.assertTrue(SnykRunner.isRequested(mockScript, false, 'develop'))
    }

    @Test
    void test_isRequested_SnykNotActive() {
        MockScript mockScript = new MockScript('Example_MyProject_DEV_CI')
        mockScript.env.SNYK_ACTIVE = 'FALSE'
        Assert.assertFalse(SnykRunner.isRequested(mockScript, false, 'develop'))
    }

    @Test
    void test_shouldMonitor_PermanentBranch() {
        MockScript mockScript = new MockScript('Example_MyProject_DEV_CI')
        mockScript.env.SNYK_ACTIVE = 'TRUE'
        SnykRunner testee = new SnykRunner(mockScript, PipelineType.DOTNET, 'develop')
        Assert.assertTrue(testee.shouldMonitor())
    }

    @Test
    void test_shouldMonitor_temporaryBranch() {
        MockScript mockScript = new MockScript('Example_MyProject_DEV_CI')
        mockScript.env.SNYK_ACTIVE = 'TRUE'
        SnykRunner testee = new SnykRunner(mockScript, PipelineType.DOTNET, 'feature/xyz')
        Assert.assertFalse(testee.shouldMonitor())

    }

    @Test
    void test_getSnykTokenName() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName jobName = new JobName(script)
        Assert.assertEquals(SnykRunner.getSnykTokenName(jobName), 'SNYK_TOKEN_RPS')

    }
}