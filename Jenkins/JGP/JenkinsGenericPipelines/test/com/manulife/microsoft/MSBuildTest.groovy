package com.manulife.microsoft

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level

import org.testng.annotations.Test
import org.testng.Assert

class MSBuildTest {
    @Test
    void testConstructor_DebugMode() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG

        MSBuild testee = new MSBuild(mockScript)
        testee.init('1.1', 'Debug')

        Assert.assertEquals(testee.exe, 'E:\\build-tools\\msbuild.exe')
        Assert.assertEquals(testee.buildType, 'Debug')
        Assert.assertEquals(testee.buildOpts, '/p:Configuration=Debug /p:DebugType=Full -v:d')
        Assert.assertEquals(testee.rebuildCmd, '\"E:\\build-tools\\msbuild.exe\" /t:rebuild /p:Configuration=Debug /p:DebugType=Full -v:d')
    }

    @Test
    void testConstructor_NonDebug() {
        MockScript mockScript = new MockScript() {
            def tool(String toolName) {
                return 'E:\\build-tools'
            }
        }
        mockScript.logger.level = Level.DEBUG

        MSBuild testee = new MSBuild(mockScript)
        testee.init('1.1', 'notDebug')

        Assert.assertEquals(testee.exe, 'E:\\build-tools\\msbuild.exe')
        Assert.assertEquals(testee.buildType, 'Release')
        Assert.assertEquals(testee.buildOpts, '/p:Configuration=Release /p:DebugType=Full -v:d')
        Assert.assertEquals(testee.rebuildCmd, '\"E:\\build-tools\\msbuild.exe\" /t:rebuild /p:Configuration=Release /p:DebugType=Full -v:d')
    }
}