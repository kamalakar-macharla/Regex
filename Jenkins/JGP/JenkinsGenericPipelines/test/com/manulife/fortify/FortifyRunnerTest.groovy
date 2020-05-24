package com.manulife.fortify

import com.manulife.jenkins.MockScript

import org.testng.annotations.Test
import org.testng.Assert

class FortifyRunnerTest {
    @Test
    void testObtainFortifyRoot_Unix() {
        MockScript mockScript = new MockScript()
        mockScript.env.HOME = 'myHome'
        String retval = FortifyRunner.obtainFortifyRoot(mockScript, true)
        Assert.assertEquals(retval, "myHome/Home/workspace/fortify")
    }

    @Test
    void testObtainFortifyRoot_Windows() {
        MockScript mockScript = new MockScript()
        mockScript.env.HOME = 'myHome'
        String retval = FortifyRunner.obtainFortifyRoot(new MockScript(), false)
        Assert.assertEquals(retval, "e:/fortify")
    }

    @Test
    void testTranslateOnly() {
        FortifyRunner testee = new FortifyRunner() {
            protected runScript(def runOpts = null) {
                return 'This is an error message'
            }
        }

        try {
            testee.translateOnly()
        }
        catch(FortifyRunnerException e) {
            // Expecting an exception here.  All good!
            return
        }

        Assert.failed('Should have failed with an exception')
    }

    @Test
    void testRunWhenPipelineTimesOut() {
        String expectedMsg = 'Project status UNKNOWN.  Fortify was unable to assess' +
                             ' if the project is compliant with Code Security Governance' +
                             ' because its execution was aborted by a job timeout' +
                             ' in the \"Jenkinsfile\" script or by a user.'
        FortifyRunner testee = new FortifyRunner() {
            protected runScript(def runOpts = null) {
                throw new org.jenkinsci.plugins.workflow.steps.FlowInterruptedException(hudson.model.Result.ABORTED)
            }
        }
        testee.scriptObj = new MockScript()

        FortifyResult fortifyResult
        try {
            fortifyResult = testee.run(null)
        }
        catch(e) {
            Assert.fail("Shouldn't have thrown an exception")
            return
        }

        Assert.assertFalse(fortifyResult.codeSecurityGatePassed)
        Assert.assertEquals(fortifyResult.message, expectedMsg)
    }

    @Test
    void testRunWhenPipelineExecutionIsAborted() {
        String expectedMsg = 'Project status UNKNOWN.  Fortify was unable to assess' +
                             ' if the project is compliant with Code Security Governance' +
                             ' because its execution was aborted by a job timeout' +
                             ' in the \"Jenkinsfile\" script or by a user.'
        FortifyRunner testee = new FortifyRunner() {
            protected runScript(def runOpts = null) {
                throw new hudson.AbortException()
            }
        }
        testee.scriptObj = new MockScript()

        FortifyResult fortifyResult
        try {
            fortifyResult = testee.run(null)
        }
        catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            Assert.fail("Shouldn't have thrown an exception")
            return
        }

        Assert.assertFalse(fortifyResult.codeSecurityGatePassed)
        Assert.assertEquals(fortifyResult.message, expectedMsg)
    }

    @Test
    void testRunWhenPipelineFailsGating() {
        FortifyRunner testee = new FortifyRunner() {
            protected runScript(def runOpts = null) {
                return 'This is a fake error msg'
            }
        }
        testee.scriptObj = new MockScript()

        FortifyResult fortifyResult
        try {
            fortifyResult = testee.run(null)
        }
        catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            Assert.fail("Shouldn't have thrown an exception")
            return
        }

        Assert.assertFalse(fortifyResult.codeSecurityGatePassed)
        Assert.assertEquals(fortifyResult.message, 'This is a fake error msg')
    }

    @Test
    void testRunWhenPipelinePassesGating() {
        MockScript mockScript = new MockScript() {
            def readFile(def fileName) {
                return 'Some text without errors'
            }
        }
        mockScript.env.WORKSPACE = "c:/somefolder"
        FortifyRunner testee = new FortifyRunner() {
            protected runScript(def runOpts = null) {
                return null
            }
        }
        testee.scriptObj = mockScript

        FortifyResult fortifyResult
        try {
            fortifyResult = testee.run(null)
        }
        catch(org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            Assert.fail("Shouldn't have thrown an exception")
            return
        }

        Assert.assertTrue(fortifyResult.codeSecurityGatePassed)
        Assert.assertEquals(fortifyResult.message, 'Project PASSED Code Security Gate!')
    }

    @Test
    void testValidateCodeSecurityGate_withScriptResult() {
        FortifyRunner testee = new FortifyRunner()
        FortifyResult fortifyResult = new FortifyResult()

        testee.validateCodeSecurityGate('script result', null, fortifyResult)
        Assert.assertEquals(fortifyResult.message, 'script result')
        Assert.assertFalse(fortifyResult.codeSecurityGatePassed)
    }

    @Test
    void testValidateCodeSecurityGate_withoutScriptResult_True_GatePassed() {
        MockScript mockScript = new MockScript() {
            def readFile(def fileName) {
                return "There are\n no issues\n in that project"
            }
        }
        mockScript.env.WORKSPACE = "c:/somefolder"
        FortifyRunner testee = new FortifyRunner()
        testee.scriptObj = mockScript
        FortifyResult fortifyResult = new FortifyResult()

        testee.validateCodeSecurityGate(null, "anything", fortifyResult)
        Assert.assertEquals(fortifyResult.message, 'Project PASSED Code Security Gate!')
        Assert.assertTrue(fortifyResult.codeSecurityGatePassed)
    }

    @Test
    void testValidateCodeSecurityGate_withoutScriptResult_True_GateFailed() {
        MockScript mockScript = new MockScript() {
            def readFile(def fileName) {
                return "Some text\n" + 
                       "4 issues of 12 matched search query.\n" +
                       "Some other text"
            }
        }
        mockScript.env.WORKSPACE = "c:/somefolder"
        FortifyRunner testee = new FortifyRunner()
        testee.scriptObj = mockScript
        FortifyResult fortifyResult = new FortifyResult()

        testee.validateCodeSecurityGate(null, "anything", fortifyResult)
        Assert.assertEquals(fortifyResult.message, 'Project FAILED Code Security Gate.  Fortify detected 4 high or critical issues such as "Some text" in Some other text')
        Assert.assertFalse(fortifyResult.codeSecurityGatePassed)
    }

    @Test
    void testIsRequested_FortifySwitch_Off() {
        MockScript mockScript = new MockScript()
        mockScript.env.FORTIFY_ACTIVE = 'FALSE'
        Assert.assertFalse(FortifyRunner.isRequested(mockScript, true, '', ''))
    }

    @Test
    void testIsRequested_FortifySwitch_Off_ForceFullScan_True() {
        MockScript mockScript = new MockScript()
        mockScript.env.FORTIFY_ACTIVE = 'FALSE'
        Assert.assertFalse(FortifyRunner.isRequested(mockScript, true, '', ''))
    }

    @Test
    void testIsRequested_FortifySwitch_On_ForceFullScan_True() {
        MockScript mockScript = new MockScript()
        mockScript.env.FORTIFY_ACTIVE = 'TRUE'
        Assert.assertTrue(FortifyRunner.isRequested(mockScript, true, '', ''))
    }

    @Test
    void testIsRequested_FortifySwitch_On_ForceFullScan_False_BranchMatchingFortifyTriggers_True() {
        MockScript mockScript = new MockScript()
        mockScript.env.FORTIFY_ACTIVE = 'TRUE'
        Assert.assertTrue(FortifyRunner.isRequested(mockScript, false, 'develop', 'develop'))
    }

    @Test
    void testIsRequested_FortifySwitch_On_ForceFullScan_False_BranchMatchingFortifyTriggers_False() {
        MockScript mockScript = new MockScript()
        mockScript.env.FORTIFY_ACTIVE = 'TRUE'
        Assert.assertFalse(FortifyRunner.isRequested(mockScript, false, 'noMatch', 'develop'))
    }
}