package com.manulife.microsoft

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level

import org.testng.annotations.Test
import org.testng.Assert

class XUnitRunnerConsoleTest {
    @Test
    void testGetArgs() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG
        MSBuild msBuild = new MSBuild(mockScript)
        msBuild.init('1.1', 'notDebug')

        XUnitRunnerConsole testee = new XUnitRunnerConsole(mockScript,
                                                           msBuild,
                                                           'projectRootFolder',
                                                           'testProjectNameAndFolder',
                                                           'testProjectName',
                                                           'xunitTestFlags')
        Assert.assertEquals(testee.getArgs(),
                            'testProjectNameAndFolder\\bin\\Release\\testProjectName.dll ' +
                            '-xml projectRootFolder/XUnitResults.xml -noshadow xunitTestFlags ')
    }
}