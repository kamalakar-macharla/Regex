package com.manulife.util

import org.testng.annotations.Test
import org.testng.Assert

class StringsTest {
    @Test
    void testTrimAndShift() {
        def s = Strings.trimAndShift("""
abc --def g
                                            hij " klm"
                                    """)
        Assert.assertEquals(s, '''abc --def g
hij " klm"''')
    }

    @Test
    void testUnwrapMultiLine() {
        def s = Strings.unwrapMultiLine('''abc
def
g''')
        Assert.assertEquals(s, "abc def g")
    }

    @Test
    void testContinueMultiLineForShell() {
        def s = Strings.continueMultiLineForShell('''--abc="foo bar"
                    --def=true
                    --g="x y z"''')
        Assert.assertEquals(s, '''--abc="foo bar" \\
 --def=true \\
 --g="x y z"''')
    }

    @Test
    void testContinueMultiLineForPowerShell() {
        def s = Strings.continueMultiLineForPowerShell('''abc
def
g''')
        Assert.assertEquals(s, '''abc `
def `
g''')
    }

    @Test
    void testEscapeForCmdBatch() {
        def s = Strings.escapeForCmdBatch('''curl --write-out "%{http_code}" "https://foo.test/?q=find%20me"''')
        Assert.assertEquals(s, '''curl --write-out "%%{http_code}" "https://foo.test/?q=find%%20me"''')
    }

    @Test
    void testMatchRegex() {
        
        def env = Strings.match('uat-deploy.properties', /^(dev|uat|prod|tst)/)
        Assert.assertEquals(env.size(), 1)
        Assert.assertEquals(env.get(0).get(0), 'uat')
    }

    @Test
    void testEscapeMultiLineShellForCmdBatchInSingleQuotes() {
        def scriptWeb = "https://git.platform.manulife.io/appsec/static_analysis/raw/master/sample-builds/"
        def fortifySSC = "https://fortify.americas.manulife.net/ssc"
        def fortifyApp = "MFC_SECENG_BLAH"
        def fortifyVer = "any"
        def fortifyAppDescr = "The flagship product: she sells sea shells by the seashore"
        def fortifyRoot = "e:/fortify"
        def fortifyOpts = " -b maven_MFC_SECENG_BLAH"
        def curlAuth = "-H \"PRIVATE-TOKEN: ABCD-EF-ghij\""
        def shellScript = """scriptweb="${scriptWeb}"
                curlauth=(${curlAuth})
                e=\$(curl "\${curlauth[@]}" -s -o fortify.sh --write-out "%{http_code}" "\${scriptweb}fortify.sh")
                (( e == 200 ))
                e=\$(curl "\${curlauth[@]}" -s -o fortify-ssc.py --write-out "%{http_code}" "\${scriptweb}fortify-ssc.py")
                (( e == 200 ))
                source fortify.sh \\
                    "${fortifySSC}" \\
                    "${fortifyApp}" "${fortifyVer}" "${fortifyAppDescr}" \\
                    "${fortifyRoot}"${fortifyOpts}
            """
        def platformScript = "\"%cygbinslash%bash.exe\" -exc '" +
                    Strings.escapeMultiLineShellForCmdBatchInSingleQuotes("""windir="\$(/usr/bin/cygpath -u "\${WINDIR}")"
                            export PATH="/bin:/usr/bin:\${windir}/System32:\${windir}/System32/WindowsPowerShell/v1.0"
                        """ + shellScript) + "'"

        Assert.assertEquals(platformScript, '''"%cygbinslash%bash.exe" -exc 'windir="$(/usr/bin/cygpath -u "${WINDIR}")"; ^\r
 export PATH="/bin:/usr/bin:${windir}/System32:${windir}/System32/WindowsPowerShell/v1.0"; ^\r
 scriptweb="https://git.platform.manulife.io/appsec/static_analysis/raw/master/sample-builds/"; ^\r
 curlauth=(-H "PRIVATE-TOKEN: ABCD-EF-ghij"); ^\r
 e=$(curl "${curlauth[@]}" -s -o fortify.sh --write-out "%%{http_code}" "${scriptweb}fortify.sh"); ^\r
 (( e == 200 )); ^\r
 e=$(curl "${curlauth[@]}" -s -o fortify-ssc.py --write-out "%%{http_code}" "${scriptweb}fortify-ssc.py"); ^\r
 (( e == 200 )); ^\r
 source fortify.sh ^\r
 "https://fortify.americas.manulife.net/ssc" ^\r
 "MFC_SECENG_BLAH" "any" "The flagship product: she sells sea shells by the seashore" ^\r
 "e:/fortify" -b maven_MFC_SECENG_BLAH\'''')

        String t = Strings.escapeMultiLineShellForCmdBatchInSingleQuotes('''export PATH="/usr/bin:/bin"
echo "This \\
is a test"''')
        Assert.assertEquals(t, '''export PATH="/usr/bin:/bin"; ^\r
 echo "This ^\r
 is a test"''')
    }

    @Test
    void testEscapeMultiLinePowerShellForCmdBatchWithoutQuotes() {
        def blackDuckExtraParams = """ "--detect.nuget.excluded.modules=Nothing_To_Exclude"
                                        "--detect.nuget.config.path=E:\\jenkins_workspaces\\CIRM_Projects\\CIRM_DevOps_Fortify_DotNet\\workspace/./nuget.config"
                                        "--detect.blackduck.signature.scanner.exclusion.name.patterns=node_modules,.sonarqube,.sonar"
                                   """
        def blackDuckParams = """
                "--blackduck.url=https://manulifefinancial.blackducksoftware.com/"
                "--blackduck.trust.cert=true"
                "--blackduck.api.token=XXXX-yyyy-zz"
                "--detect.tools=DETECTOR"
                "--logging.level.com.blackducksoftware.integration=INFO"
                "--detect.project.name=MFC_SECENG_BLAH"
                "--detect.project.version.name=dev"
                "--detect.project.version.phase=DEVELOPMENT"
                "--detect.project.version.distribution=INTERNAL"
                "--detect.code.location.name=MFC_SECENG_BLAH-dev"
                "--detect.report.timeout=${10 * 60 * 1000}"
                "--detect.policy.check.fail.on.severities=CRITICAL,BLOCKER,MAJOR"
                "--detect.detector.search.depth=3"
                "--detect.detector.search.continue=true"
                "--detect.source.path=."
                "--detect.blackduck.signature.scanner.exclusion.name.patterns=/Nothing/To/Exclude/"
                "--detect.risk.report.pdf=true"
                "--detect.risk.report.pdf.path=E:\\jenkins_workspaces\\CIRM_Projects\\CIRM_DevOps_Fortify_DotNet\\workspace"
            """ + blackDuckExtraParams

        def psscript = Strings.trimAndShift("""
                \$VerbosePreference = 'Continue'
                \$DebugPreference = 'Continue'
                [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
                irm https://blackducksoftware.github.io/hub-detect/hub-detect.ps1?\$(Get-Random) | iex
                exit detect """ + Strings.continueMultiLineForPowerShell(blackDuckParams))

        // Prepare a bat invokation to show progress (invoking as powershell shows no progress).
        def script = Strings.escapeMultiLinePowerShellForCmdBatchWithoutQuotes(psscript)

        // In the triple-single-quoted expected string below
        //      the backslash-r \r digraphs expand to the raw carriage return,
        //      the backslash-backslash \\ digraphs expand to the raw backslash.
        //
        // The backslash-doublequotes \" digraphs become doublequotes within
        // the triple-single-quotes, contrary to the documentation.  To expect
        // a backslash followed by a double quote, we escape the backslash.
        //
        //  http://groovy-lang.org/syntax.html#_triple_single_quoted_string

        // The ..WithoutQuotes call above avoids wrapping the -Command value in
        // double quotes to accommodate CMD.EXE failing to follow multiple
        // lines when using the raw double quotes.
        Assert.assertEquals(script, '''$VerbosePreference = 'Continue'; ^\r
 $DebugPreference = 'Continue'; ^\r
 [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^\r
 irm https://blackducksoftware.github.io/hub-detect/hub-detect.ps1?$(Get-Random) ^| iex; ^\r
 exit detect \\"--blackduck.url=https://manulifefinancial.blackducksoftware.com/\\" ^\r
 \\"--blackduck.trust.cert=true\\" ^\r
 \\"--blackduck.api.token=XXXX-yyyy-zz\\" ^\r
 \\"--detect.tools=DETECTOR\\" ^\r
 \\"--logging.level.com.blackducksoftware.integration=INFO\\" ^\r
 \\"--detect.project.name=MFC_SECENG_BLAH\\" ^\r
 \\"--detect.project.version.name=dev\\" ^\r
 \\"--detect.project.version.phase=DEVELOPMENT\\" ^\r
 \\"--detect.project.version.distribution=INTERNAL\\" ^\r
 \\"--detect.code.location.name=MFC_SECENG_BLAH-dev\\" ^\r
 \\"--detect.report.timeout=600000\\" ^\r
 \\"--detect.policy.check.fail.on.severities=CRITICAL,BLOCKER,MAJOR\\" ^\r
 \\"--detect.detector.search.depth=3\\" ^\r
 \\"--detect.detector.search.continue=true\\" ^\r
 \\"--detect.source.path=.\\" ^\r
 \\"--detect.blackduck.signature.scanner.exclusion.name.patterns=/Nothing/To/Exclude/\\" ^\r
 \\"--detect.risk.report.pdf=true\\" ^\r
 \\"--detect.risk.report.pdf.path=E:\\\\jenkins_workspaces\\\\CIRM_Projects\\\\CIRM_DevOps_Fortify_DotNet\\\\workspace\\" ^\r
 \\"--detect.nuget.excluded.modules=Nothing_To_Exclude\\" ^\r
 \\"--detect.nuget.config.path=E:\\\\jenkins_workspaces\\\\CIRM_Projects\\\\CIRM_DevOps_Fortify_DotNet\\\\workspace/./nuget.config\\" ^\r
 \\"--detect.blackduck.signature.scanner.exclusion.name.patterns=node_modules,.sonarqube,.sonar\\"''')

        String pscmd = Strings.escapeMultiLinePowerShellForCmdBatchWithoutQuotes('''$VerbosePreference = 'Continue'
 exit detect ''' + Strings.continueMultiLineForPowerShell('''"--foo.bar=baz"
 "--foo.baz=-s settings.xml -B"
 "--quux"'''))
        Assert.assertEquals(pscmd, '''$VerbosePreference = 'Continue'; ^\r
 exit detect \\"--foo.bar=baz\\" ^\r
 \\"--foo.baz=-s settings.xml -B\\" ^\r
 \\"--quux\\"''')
    }

    @Test
    void testRemoveFirstLine() {
        def s = Strings.removeFirstLine('''c:\\dir> foobar
Hello, world!
''')
        Assert.assertEquals(s, '''Hello, world!
''')
    }

    @Test
    void testRemoveCarriageReturns() {
        def s = Strings.removeCarriageReturns('''\r\nabc\r\ndef\r\nghi\r\n''')
        Assert.assertEquals(s, '''\nabc\ndef\nghi\n''')
        def t = Strings.removeCarriageReturns('''\rabc\r\ndef\rghi\r''')
        Assert.assertEquals(t, '''abc\ndefghi''')
    }


    @Test
    void testSolidify() {
        def s = Strings.solidify('''-s settings.xml compile''')
        Assert.assertEquals(s, '''-s\\\\ settings.xml\\\\ compile''')
    }


    @Test
    void testSolidifyParams() {
        def s = Strings.solidifyParams('''
    "--detect.foo=bar baz"
    "--detect.bar.quux=n\'importe quoi"
    "--detect.baz=true"
    "--detect.quux=false"
''')
        Assert.assertEquals(s, '''
    "--detect.foo=bar\\\\ baz"
    "--detect.bar.quux=n\'importe\\\\ quoi"
    "--detect.baz=true"
    "--detect.quux=false"
''')

        def t = Strings.solidifyParams('''"--detect.foo=bar baz" "--detect.qux.quux=quuz corge grault"''')
        Assert.assertEquals(t, '''"--detect.foo=bar\\\\ baz" "--detect.qux.quux=quuz\\\\ corge\\\\ grault"''')


        def u = Strings.solidifyParams('''qq=%batq%"%batq%
foobar.sh "--detect.foo=bar baz" \\
    "--detect.qux.quux=quuz corge grault"''')
        Assert.assertEquals(u, '''qq=%batq%"%batq%
foobar.sh "--detect.foo=bar\\\\ baz" \\
    "--detect.qux.quux=quuz\\\\ corge\\\\ grault"''')


        def v = Strings.solidifyParams('''
    "--blackduck.url=https://manulifefinancial.blackducksoftware.com/"

        "--blackduck.username=****"
        "--blackduck.password=****" "--detect.tools=DETECTOR"
    "--logging.level.com.synopsys.integration=DEBUG"
    "--detect.project.name=MFC_CIRM_Java"
    "--detect.project.version.name=master"
    "--detect.project.version.phase=DEVELOPMENT"
    "--detect.project.version.distribution=INTERNAL"
    "--detect.code.location.name=MFC_CIRM_Java-master"
    "--detect.bom.aggregate.name=MFC_CIRM_Java-master"
    "--detect.report.timeout=3600"
    "--detect.policy.check.fail.on.severities=CRITICAL,BLOCKER,MAJOR"
    "--detect.detector.search.depth=3"
    "--detect.detector.search.continue=true"
    "--detect.source.path=."
    "--detect.blackduck.signature.scanner.exclusion.name.patterns=/Nothing/To/Exclude/,.sonar,.sonarqube,node_modules"
    "--detect.risk.report.pdf=true"
    "--detect.risk.report.pdf.path=/Users/dsmacbuild/Home/workspace/CIRM_Projects/CIRM_Test_Pipeline/CIRM_DotNet_bdnuget/CIRM_Java"
    "--detect.wait.for.results=true"

                            "--detect.maven.build.command=-s /Users/dsmacbuild/Home/workspace/CIRM_Projects/CIRM_Test_Pipeline/CIRM_DotNet_bdnuget/CIRM_Java/settings.xml -B"
                            "--detect.maven.path=/usr/local/apache-maven-3.3.9/bin/mvn"
                            "--detect.maven.excluded.modules=Nothing_To_Exclude"
                            "--detect.maven.scope=runtime"

''')
	Assert.assertEquals(v, '''
    "--blackduck.url=https://manulifefinancial.blackducksoftware.com/"

        "--blackduck.username=****"
        "--blackduck.password=****" "--detect.tools=DETECTOR"
    "--logging.level.com.synopsys.integration=DEBUG"
    "--detect.project.name=MFC_CIRM_Java"
    "--detect.project.version.name=master"
    "--detect.project.version.phase=DEVELOPMENT"
    "--detect.project.version.distribution=INTERNAL"
    "--detect.code.location.name=MFC_CIRM_Java-master"
    "--detect.bom.aggregate.name=MFC_CIRM_Java-master"
    "--detect.report.timeout=3600"
    "--detect.policy.check.fail.on.severities=CRITICAL,BLOCKER,MAJOR"
    "--detect.detector.search.depth=3"
    "--detect.detector.search.continue=true"
    "--detect.source.path=."
    "--detect.blackduck.signature.scanner.exclusion.name.patterns=/Nothing/To/Exclude/,.sonar,.sonarqube,node_modules"
    "--detect.risk.report.pdf=true"
    "--detect.risk.report.pdf.path=/Users/dsmacbuild/Home/workspace/CIRM_Projects/CIRM_Test_Pipeline/CIRM_DotNet_bdnuget/CIRM_Java"
    "--detect.wait.for.results=true"

                            "--detect.maven.build.command=-s\\\\ /Users/dsmacbuild/Home/workspace/CIRM_Projects/CIRM_Test_Pipeline/CIRM_DotNet_bdnuget/CIRM_Java/settings.xml\\\\ -B"
                            "--detect.maven.path=/usr/local/apache-maven-3.3.9/bin/mvn"
                            "--detect.maven.excluded.modules=Nothing_To_Exclude"
                            "--detect.maven.scope=runtime"

''')
    }

    @Test
    void testCanonicalizeAppKey() {
        def s = Strings.canonicalizeAppKey("GRS_member_DEV_CI")
        Assert.assertEquals(s, "GRS_member")
    }

    @Test
    void testXmlEncodeAttr() {
        def s = Strings.xmlEncodeAttr("testing ' \" & < > here")
        Assert.assertEquals(s, "testing &apos; &quot; &amp; &lt; &gt; here")
    }

    @Test
    void testDeBOM() {
        def s = Strings.deBOM("testing")
        Assert.assertEquals(s, "testing")

        s = Strings.deBOM("\uFEFFtesting")
        Assert.assertEquals(s, "testing")

        s = Strings.deBOM("\uFEFF")
        Assert.assertEquals(s, "")

        s = Strings.deBOM("")
        Assert.assertEquals(s, "")

        s = Strings.deBOM(null)
        Assert.assertEquals(s, null)

        byte[] sillyDiskFile = "\uFEFFnow this".getBytes("UTF-8")
        Assert.assertEquals(sillyDiskFile[0..2].collect{it & 0xFF}, [0xEF, 0xBB, 0xBF])
        String stringFile = new String(sillyDiskFile, "UTF-8")
        Assert.assertEquals(stringFile, "\uFEFFnow this")
        s = Strings.deBOM(stringFile)
        Assert.assertEquals(s, "now this")

        String one = "one"
        s = Strings.deBOM("\uFEFF${one} more")
        Assert.assertEquals(s, "one more")
    }
}
