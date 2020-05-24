package com.manulife.git

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class GitFlowTest {
    @Test
    void testgetParentBranch_Exists_Feature() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc\nremotes/origin/develop'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('feature/abc')
        Assert.assertEquals(parentBranch, 'develop')
    }

    @Test
    void testgetParentBranch_Exists_Develop() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc\nremotes/origin/develop'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('develop')
        //Temp solution to check master instead of release 
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_Exists_Fix() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nremotes/origin/feature/abc\nremotes/origin/develop'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('fix/abc')
        Assert.assertEquals(parentBranch, 'release')
    }

    @Test
    void testgetParentBranch_Exists_Release() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nmaster\nremotes/origin/feature/abc\nremotes/origin/develop'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('release')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_Exists_HotFix() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'remotes/origin/release\nmaster\nremotes/origin/feature/abc\nremotes/origin/develop'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('hotfix/abc')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_DoesntExist_Feature() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'nothing'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('feature/abc')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_DoesntExist_Develop() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'nothing'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('develop')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_DoesntExist_Fix() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'nothing'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('fix/abc')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_DoesntExist_Release() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'nothing'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('release')
        Assert.assertEquals(parentBranch, 'master')
    }

    @Test
    void testgetParentBranch_DoesntExist_HotFix() {
        def scriptObj = new MockScript([unix: true, exitCode: 0, shRetVal: 'nothing'])
        def testee = new GitFlow(scriptObj, GitFlowType.GITFLOW)
        String parentBranch = testee.getParentBranch('hotfix/abc')
        Assert.assertEquals(parentBranch, 'master')
    }
}