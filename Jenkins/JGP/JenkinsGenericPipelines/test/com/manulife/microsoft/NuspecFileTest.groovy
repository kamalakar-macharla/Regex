package com.manulife.microsoft

import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.util.Strings
import com.manulife.versioning.SemVersion

import net.sf.json.JSONSerializer;
import org.testng.annotations.Test
import org.testng.Assert

class NuspecFileTest {
    static String sampleXml = """<?xml version="1.0"?>
<package>
  <metadata>
    <id>PersonMatchApi</id>
    <version>0.0.1-ALPHA</version>
        <authors>Manulife</authors>
    <owners>Manulife</owners>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <description>Microservice for PersonMatchApi</description>
    <releaseNotes>Pushed to Artifactory</releaseNotes>
    <copyright>Copyright 2018</copyright>
    <tags>PersonMatchApi</tags>
  </metadata>
  <files>
        <file src="PersonMatchApi\\bin\\Debug\\netcoreapp2.0\\publish\\**\\*.*" target="publish"/>
    </files>
</package>"""

    class MockScript extends Script {
        def env = [:]
        def logger = new Logger(this, Level.INFO)
        def printed

        Object run() {
            println("Running...")
        }

        def ansiColor(String str, Closure cl) {
            cl()
        }

        def echo(String message) {
            println("${message}")
            printed = message
        }

        def readJSON(def params) {
            def version = ['version':'0.0.1-ALPHA']
            def metadata = ['metadata':version]
            def retval = ['package':metadata]
            return retval
        }
    }

    @Test
    void testReadCurrentVersion() {
        NuspecFile testee = new NuspecFile(new MockScript(), sampleXml)
        Assert.assertEquals(testee.getVersion().toString(), "0.0.1-ALPHA")
    }

    @Test
    void testReadCurrentVersionBOM() {
        NuspecFile testee = new NuspecFile(new MockScript(), Strings.deBOM("\uFEFF" + sampleXml))
        Assert.assertEquals(testee.getVersion().toString(), "0.0.1-ALPHA")
    }

    @Test
    void testSetVersion() {
        MockScript mockScript = new MockScript()
        NuspecFile testee = new NuspecFile(mockScript, Strings.deBOM(sampleXml))
        testee.setVersion(SemVersion.parse(mockScript, "0.0.1"))
        String updContent = testee.getXML()
        Assert.assertEquals(updContent, """<?xml version="1.0" encoding="UTF-8"?><package>
  <metadata>
    <id>PersonMatchApi</id>
    <version>0.0.1</version>
    <authors>Manulife</authors>
    <owners>Manulife</owners>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <description>Microservice for PersonMatchApi</description>
    <releaseNotes>Pushed to Artifactory</releaseNotes>
    <copyright>Copyright 2018</copyright>
    <tags>PersonMatchApi</tags>
  </metadata>
  <files>
    <file src="PersonMatchApi\\bin\\Debug\\netcoreapp2.0\\publish\\**\\*.*" target="publish"/>
  </files>
</package>
""")
    }

    @Test
    void testUpdateInputPathsInXML() {
        NuspecFile testee = new NuspecFile(new MockScript(), Strings.deBOM(sampleXml))
        boolean changed
        String projFrameworkFromNuspec
        (changed, projFrameworkFromNuspec) = testee.updateInputPathsInXML("Release", "2.2")
        Assert.assertTrue(changed)
        Assert.assertEquals(testee.getXML(), """<?xml version="1.0" encoding="UTF-8"?><package>
  <metadata>
    <id>PersonMatchApi</id>
    <version>0.0.1-ALPHA</version>
    <authors>Manulife</authors>
    <owners>Manulife</owners>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <description>Microservice for PersonMatchApi</description>
    <releaseNotes>Pushed to Artifactory</releaseNotes>
    <copyright>Copyright 2018</copyright>
    <tags>PersonMatchApi</tags>
  </metadata>
  <files>
    <file src="PersonMatchApi\\bin\\Release\\netcoreapp2.0\\publish\\**\\*.*" target="publish"/>
  </files>
</package>
""")
        Assert.assertEquals(projFrameworkFromNuspec, "netcoreapp2.0")
    }
}
