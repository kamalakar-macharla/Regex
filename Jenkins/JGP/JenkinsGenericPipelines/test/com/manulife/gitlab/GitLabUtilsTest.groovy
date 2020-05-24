package com.manulife.gitlab

import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class GitLabUtilsTest {
    static class MockScript extends Script {
        def unix
        def env = [:]
        def pipelineParams = [:]
        def exitCode
        def logger = new Logger(this, Level.DEBUG)
        def currentBuild = [:]

        Object run() {
            println("Running...")
        }

        def echo(String message) {
//            println(message)
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
    }

    @Test
    void testGetCommitsList_Linux_NoCommit_HappyPath() {
        def linuxScript = new MockScript([unix: true, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return '' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('', commitList)
    }

    @Test
    void testGetCommitsList_Linux_OnlyOneCommit_HappyPath() {
        def linuxScript = new MockScript([unix: true, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return 'abcdefghij' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('abcdefghij', commitList)
    }

    @Test
    void testGetCommitsList_Linux_MoreThanOneCommit_HappyPath() {
        def linuxScript = new MockScript([unix: true, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return 'abcdefghij\npoiuytre' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('abcdefghij,poiuytre', commitList)
    }

    @Test
    void testGetCommitsList_Windows_NoCommit_HappyPath() {
        def linuxScript = new MockScript([unix: false, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return 'git log targetBranch..sourceBranch --pretty=\n\n' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('', commitList)
    }

    @Test
    void testGetCommitsList_Windows_OnlyOneCommit_HappyPath() {
        def linuxScript = new MockScript([unix: false, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return 'git log targetBranch..sourceBranch --pretty=\n\nabcdefghij' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('abcdefghij', commitList)
    }

    @Test
    void testGetCommitsList_Windows_MoreThanOneCommit_HappyPath() {
        def linuxScript = new MockScript([unix: false, exitCode: 0])
        GitLabUtils.metaClass.static.getCommits = { Script scriptObj -> return 'git log targetBranch..sourceBranch --pretty=\n\nabcdefghij\npoiuytre' }
        String commitList = GitLabUtils.getCommitsList(linuxScript, ',')
        Assert.assertEquals('abcdefghij,poiuytre', commitList)
    }

    @Test
    void testPipelineResultToGitLabPipelineStatus_SUCCESS() {
        MockScript script = new MockScript()
        script.setCurrentResult('SUCCESS')
        String retval = GitLabUtils.pipelineResultToGitLabPipelineStatus(script)
        Assert.assertEquals(retval, 'success')
    }

    @Test
    void testPipelineResultToGitLabPipelineStatus_UNSTABLE() {
        MockScript script = new MockScript()
        script.setCurrentResult('UNSTABLE')
        String retval = GitLabUtils.pipelineResultToGitLabPipelineStatus(script)
        Assert.assertEquals(retval, 'failed')
    }

    @Test
    void testPipelineResultToGitLabPipelineStatus_ERROR() {
        MockScript script = new MockScript()
        script.setCurrentResult('ERROR')
        String retval = GitLabUtils.pipelineResultToGitLabPipelineStatus(script)
        Assert.assertEquals(retval, 'failed')
    }

    @Test
    void testPipelineResultToGitLabPipelineStatus_ABORTED() {
        MockScript script = new MockScript()
        script.setCurrentResult('ABORTED')
        String retval = GitLabUtils.pipelineResultToGitLabPipelineStatus(script)
        Assert.assertEquals(retval, 'canceled')
    }

    @Test
    void testPipelineResultToGitLabPipelineStatus_UNDEFINED() {
        MockScript script = new MockScript()
        script.setCurrentResult('BAD_VALUE')
        String retval = GitLabUtils.pipelineResultToGitLabPipelineStatus(script)
        Assert.assertEquals(retval, 'failed')
    }
}
