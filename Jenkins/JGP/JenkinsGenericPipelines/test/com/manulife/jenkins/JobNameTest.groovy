package com.manulife.jenkins

import org.testng.annotations.Test
import org.testng.Assert

/**
 *
 * Represents the current Jenkins job name.
 *
 **/
class JobNameTest {
    class MockScript extends Script {
        MockScript(String scripName) {
            this.JOB_NAME = scripName
        }

        Object run() {
            println("Running...")
        }
    }

    @Test
    void testConstructor_Canada_HappyPath() {
        Script script = new MockScript("GB_Projects/GB_MyProject/GB_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getJobName(), "MFC_GB_MyProject_DEV_CI")
    }

    @Test
    void testConstructor_JH_HappyPath() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getJobName(), "JH_RPS_MyProject_DEV_CI")
    }

    @Test
    void testGetJobName_HappyPath() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getJobName(), "JH_RPS_MyProject_DEV_CI")
    }

    @Test
    void testGetProjectName_HappyPath() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getProjectName(), "JH_RPS_MyProject")
    }

    @Test
    void testGetSegmentName_JH_HappyPath() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getSegmentName(), "JH")
    }

    @Test
    void testGetBUName_HappyPath() {
        Script script = new MockScript("JH_Projects/JH_MyProject/JH_RPS_MyProject_DEV_CI")
        JobName testee = new JobName(script)
        Assert.assertEquals(testee.getBUName(), "RPS")
    }
}
