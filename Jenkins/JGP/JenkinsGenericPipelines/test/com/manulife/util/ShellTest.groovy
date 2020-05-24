package com.manulife.util

import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class ShellTest {
    static class MockScript extends Script {
        def unix
        def exitCode
        def env = [:]
        def logger = new Logger(this, Level.INFO)

        Object run() {
            println("Running...")
        }

        def echo(String message) {
            println(message)
        }

        def isUnix() {
            return unix
        }

        def sh(def params = null) {
            Boolean returnStdout = params.returnStdout
            String script = params.script
            if(exitCode) {
                throw new Exception("Shell script ${script} failed with exit code ${exitCode}")
            }
            stdout = "Subjected to a mock execution by Shell: ${script}"
            if(returnStdout) {
                return stdout
            }
            else {
                println stdout
            }
        }

        def bat(def params = null) {
            Boolean returnStdout = params.returnStdout
            String script = params.script
            if(exitCode) {
                throw new Exception("CMD script ${script} failed with exit code ${exitCode}")
            }
            stdout = "Subjected to a mock execution by CMD: ${script}"
            if(returnStdout) {
                return stdout
            }
            else {
                println stdout
            }
        }
    }

    @Test
    void testWindowsError() {
        def windowsScript = new MockScript([unix: false, exitCode: 1])
        def exc = ""
        try {
            Shell.quickShell(windowsScript, "java -XshowSettings:properties 2>&1")
        }
        catch(Exception e) {
            exc = e.getMessage()
        }
        Assert.assertEquals(exc, "CMD script @java -XshowSettings:properties 2>&1 failed with exit code 1")
    }

    @Test
    void testCmdInWindows() {
        def windowsScript = new MockScript([unix: false, exitCode: 0])
        def output = Shell.quickShell(windowsScript, "java -XshowSettings:properties 2>&1")
        Assert.assertEquals(output, "Subjected to a mock execution by CMD: @java -XshowSettings:properties 2>&1")
    }

    @Test
    void testPosixInWindows() {
        def windowsScript = new MockScript([unix: false, exitCode: 0])
        def output = Shell.quickShell(windowsScript, "ls -al foobar", null, false)
        Assert.assertEquals(output, "Subjected to a mock execution by CMD: @ls -al foobar")

        def cygwinScript = new MockScript([unix: false, exitCode: 0, env: ["CYGBINSLASH": "c:\\cygwin64\\bin\\"]])
        def cygwinOutput = Shell.quickShell(cygwinScript,
                "ls -al foob* 2> /dev/null | grep bar || :", null, false)
        Assert.assertEquals(cygwinOutput, "Subjected to a mock execution by CMD: \
@c:\\cygwin64\\bin\\ls -al foob* 2> nul | c:\\cygwin64\\bin\\grep bar || ver>nul")
    }

    @Test
    void testPosix() {
        def posixScript = new MockScript([unix: true, exitCode: 0])
        def output = Shell.quickShell(posixScript, "ls -al foobar")
        Assert.assertEquals(output, "Subjected to a mock execution by Shell: ls -al foobar")
    }
}
