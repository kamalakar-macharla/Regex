package com.manulife.git

import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class GitRepoTest {
    static class MockScript extends Script {
        def unix
        def env = [:]
        def pipelineParams = [:]
        def exitCode
        def shRetVal
        def batRetVal
        def logger = new Logger(this, Level.DEBUG)
        def currentBuild = [:]

        Object run() {
            println("Running...")
        }

        def echo(String message) {
            // println(message)
        }

        def isUnix() {
            return unix
        }

        def ansiColor(String str, Closure cl) {
            cl()
        }

        def setCurrentResult(String currentResult) {
            currentBuild.currentResult = currentResult
        }

        def sh(def params = null) {
            return shRetVal
        }

        def bat(def params = null) {
            return batRetVal
        }
    }

    @Test
    void testGetBranches_Linux_HappyPath() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc'])
        def branches = new GitRepo(scriptObj).getBranches()
        Assert.assertEquals(branches.size(), 2)
        Assert.assertEquals(branches[0], 'remotes/origin/release')
        Assert.assertEquals(branches[1], 'remotes/origin/feature/abc')
    }

    @Test
    void testGetBranches_Windows_HappyPath() {
        def scriptObj = new MockScript([unix: false, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc'])
        def branches = new GitRepo(scriptObj).getBranches()
        Assert.assertEquals(branches.size(), 2)
        Assert.assertEquals(branches[0], 'remotes/origin/release')
        Assert.assertEquals(branches[1], 'remotes/origin/feature/abc')
    }

    @Test
    void testhasBranch_Exists_Linux_HappyPath() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: '* remotes/origin/release\nremotes/origin/feature/abc'])
        boolean hasBranch = new GitRepo(scriptObj).hasBranch('remotes/origin/release')
        Assert.assertTrue(hasBranch, 'This branch exists and should be found.')
    }

    @Test
    void testhasBranch_DoesntExist_Linux_HappyPath() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc'])
        boolean hasBranch = new GitRepo(scriptObj).hasBranch('remotes/origin/develop')
        Assert.assertFalse(hasBranch, 'This branch does not exist and should not be found.')
    }
}