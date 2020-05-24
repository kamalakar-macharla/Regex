package com.manulife.report

import com.manulife.logger.Level
import com.manulife.logger.Logger

import org.testng.annotations.Test
import org.testng.Assert

class ConfigurationReportTest {
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
            printed = message
        }
    }

    @Test
    void test_print_simpleCase() {
        def configuration = ['jenkinsJobInitialAgent':'windows', 'jenkinsJobSecretToken':'mysecret', 'jenkinsJobRegEx':'^dev.*', 'anotherone':'anothervalue']
        MockScript mockScript = new MockScript()
        ConfigurationReport testee = new ConfigurationReport(mockScript, configuration)
        testee.print()
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobInitialAgent = windows'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobSecretToken = ********'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobRegEx = ^dev.*'))
        Assert.assertTrue(mockScript.printed.contains('anotherone = anothervalue'))
    }

    @Test
    void test_print_suspiciousNodeLabel() {
        def configuration = ['jenkinsJobInitialAgent':'unknown', 'jenkinsJobSecretToken':'mysecret', 'jenkinsJobRegEx':'^dev.*', 'anotherone':'anothervalue']
        MockScript mockScript = new MockScript()
        ConfigurationReport testee = new ConfigurationReport(mockScript, configuration)
        testee.print()
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobInitialAgent = unknown.  Consider using one of android_cicd_capable, concourse, ios_cicd_capable, linux, multi-platform-general, windows instead.'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobSecretToken = ********'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobRegEx = ^dev.*'))
        Assert.assertTrue(mockScript.printed.contains('anotherone = anothervalue'))
    }

    @Test
    void test_print_suspiciousRegex() {
        def configuration = ['jenkinsJobInitialAgent':'windows', 'jenkinsJobSecretToken':'mysecret', 'jenkinsJobRegEx':'^[dev].*', 'anotherone':'anothervalue']
        MockScript mockScript = new MockScript()
        ConfigurationReport testee = new ConfigurationReport(mockScript, configuration)
        testee.print()
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobInitialAgent = windows'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobSecretToken = ********'))
        Assert.assertTrue(mockScript.printed.contains('jenkinsJobRegEx = ^[dev].*.  [] such as in ^[dev] should not be used in regex since it will match any branch name that contains any of those charaters.'))
        Assert.assertTrue(mockScript.printed.contains('anotherone = anothervalue'))
    }
}