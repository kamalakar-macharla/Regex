package com.manulife.microsoft

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level

import org.testng.annotations.Test
import org.testng.Assert

class OpenCoverTest {
    @Test
    void testConstructor_noRuntimeProjectsAndNoTestProjects() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG

        XUnitRunnerConsole xUnitRunnerConsole = getXUnitRunnerConsole(mockScript)

        OpenCover testee = new OpenCover(mockScript,
                                         xUnitRunnerConsole,
                                         null,
                                         'projectName',
                                         null,
                                         'testProjectName')
        Assert.assertEquals(testee.xunitRunnerConsole, xUnitRunnerConsole)
        Assert.assertEquals(testee.whiteList, '+[projectName*]*')
        Assert.assertEquals(testee.blackList, '-[testProjectName*]* -[xunit*]* -[Moq*]*')
    }

    @Test
    void testConstructor_withRuntimeProjectsAndTestProjects() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG

        XUnitRunnerConsole xUnitRunnerConsole = getXUnitRunnerConsole(mockScript)

        OpenCover testee = new OpenCover(mockScript,
                                         xUnitRunnerConsole,
                                         'project1,project2',
                                         null,
                                         'testProject1,testProject2',
                                         null)
        Assert.assertEquals(testee.xunitRunnerConsole, xUnitRunnerConsole)
        Assert.assertEquals(testee.whiteList, '+[project1*]* +[project2*]*')
        Assert.assertEquals(testee.blackList, '-[testProject1*]* -[testProject2*]* -[xunit*]* -[Moq*]*')
    }

    @Test
    void testGetCmd() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG
        mockScript.env.WORKSPACE = '.'

        XUnitRunnerConsole xUnitRunnerConsole = getXUnitRunnerConsole(mockScript)

        OpenCover testee = new OpenCover(mockScript,
                                         xUnitRunnerConsole,
                                         'project1,project2',
                                         null,
                                         'testProject1,testProject2',
                                         null)
        Assert.assertEquals(testee.getCmd('testFolder', false), 
                                          'OpenCover.4.7.922\\tools\\OpenCover.Console.exe -target:"xunit.runner.console.2.4.1\\tools\\net452\\xunit.console.exe" -targetargs:"testProjectNameAndFolder\\bin\\Release\\testProjectName.dll -xml projectRootFolder/XUnitResults.xml -noshadow xunitTestFlags " -filter:"+[project1*]* +[project2*]* -[testProject1*]* -[testProject2*]* -[xunit*]* -[Moq*]*" -output:testFolder/coverage.opencover.xml -oldStyle -register:user')
    }

    XUnitRunnerConsole getXUnitRunnerConsole(MockScript mockScript) {
        MSBuild msBuild = new MSBuild(mockScript)
        msBuild.init('1.1', 'notDebug')

        XUnitRunnerConsole xUnitRunnerConsole = new XUnitRunnerConsole(mockScript,
                                                           msBuild,
                                                           'projectRootFolder',
                                                           'testProjectNameAndFolder',
                                                           'testProjectName',
                                                           'xunitTestFlags')
    }
}