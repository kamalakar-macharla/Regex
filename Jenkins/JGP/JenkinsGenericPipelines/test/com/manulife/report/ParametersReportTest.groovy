package com.manulife.report

import com.manulife.logger.Level
import com.manulife.logger.Logger

import org.testng.annotations.Test
import org.testng.Assert

class ParametersReportTest {
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
        def params = ['param1':'value1']
        MockScript mockScript = new MockScript()
        ParametersReport testee = new ParametersReport(mockScript, params)
        testee.print()
        Assert.assertTrue(mockScript.printed.contains('param1 = value1'))
    }

    @Test
    void test_print_noparams() {
        def configuration = []
        MockScript mockScript = new MockScript()
        ParametersReport testee = new ParametersReport(mockScript, configuration)
        testee.print()
        Assert.assertTrue(mockScript.printed.contains('**********  Pipeline Parameters  **********'))
    }
}