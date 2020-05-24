package com.manulife.microsoft

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level

import org.testng.annotations.Test
import org.testng.Assert

class NugetConfigFileTest {
    @Test
    void testGetRepoUrl() {
        MockScript mockScript = new MockScript()
        mockScript.env.ARTIFACTORY_URL = 'http://artifactory.com'
        String retval = NugetConfigFile.getRepoUrl(mockScript, 'myRepo')
        Assert.assertEquals(retval, 'http://artifactory.com/api/nuget/myRepo')
    }

    @Test
    void testGetFileNameAndPath() {
        MockScript mockScript = new MockScript() {
            def pwd(def params) {
                return '/fake/path'
            }
        }
        String retval = NugetConfigFile.getFileNameAndPath(mockScript)
        Assert.assertEquals(retval, '/fake/path/NuGet.Config')
    }
}