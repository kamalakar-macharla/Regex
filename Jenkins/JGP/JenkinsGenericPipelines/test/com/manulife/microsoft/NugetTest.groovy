package com.manulife.microsoft

import com.manulife.jenkins.MockScript
import com.manulife.logger.Level

import org.testng.annotations.Test
import org.testng.Assert

class NugetTest {
    @Test
    void testGetRestoreCmd() {
        MockScript mockScript = new MockScript() {
            def pwd(def params) {
                return '/fake/path'
            }
        }
        
        String retval = Nuget.getRestoreCmd(mockScript)
        Assert.assertEquals(retval, ' "E:/build-tools/microsoft/nuget/4.9.1/nuget.exe" restore -ConfigFile "/fake/path/NuGet.Config" ')
    }

    @Test
    void testGetRestoreCmdWithSolutionDir() {
        MockScript mockScript = new MockScript() {
            def pwd(def params) {
                return '/fake/path'
            }
        }
        
        String retval = Nuget.getRestoreCmd(mockScript, '/my/solution/dir')
        Assert.assertEquals(retval, ' "E:/build-tools/microsoft/nuget/4.9.1/nuget.exe" restore -ConfigFile "/fake/path/NuGet.Config" -SolutionDirectory /my/solution/dir')
    }

    @Test
    void testGetInstallCmd() {
        MockScript mockScript = new MockScript() {
            def pwd(def params) {
                return '/fake/path'
            }
        }
        
        String retval = Nuget.getInstallCmd(mockScript, 'myPackage', '1.2.3')
        Assert.assertEquals(retval, 'E:/build-tools/microsoft/nuget/4.9.1/nuget.exe install -ConfigFile /fake/path/NuGet.Config myPackage -Version 1.2.3')
    }
}