package com.manulife.blackduck

import org.testng.annotations.Test
import org.testng.Assert

class ExitCodeTypeTest {
    @Test
    void testLookup() {
        Assert.assertEquals(ExitCodeType.lookup(-1), ExitCodeType.UNEXPECTED)
    }

    void testGetExitCode() {
        Assert.assertEquals(ExitCodeType.FAILURE_TIMEOUT.exitCode, 2)
    }

    void testGetDescr() {
        Assert.assertEquals('Unable to scan the project with BlackDuck within the defined timeframe.  Scan was aborted because of a timeout.', 2)
    }
}