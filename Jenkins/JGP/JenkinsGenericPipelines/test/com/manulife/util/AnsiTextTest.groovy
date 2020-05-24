package com.manulife.util

import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class AnsiTextTest {
    static class MockScript extends Script {
        def logger = new Logger(this, Level.INFO)

        def ansiColor(String mode, Closure cl) {
            cl()
        }

        Object run() {
            println("Running...")
        }
    }

    @Test
    void testEmptyText_HappyPath() {
        AnsiText testee = new AnsiText(new MockScript());
        Assert.assertEquals(testee.getText(), '')
    }

    @Test
    void testWithNonAnsiText_HappyPath() {
        AnsiText testee = new AnsiText(new MockScript());
        testee.addLine('This is some text')
        testee.addLine('This is some other text')
        Assert.assertEquals(testee.getText(), 'This is some text\nThis is some other text\n')
    }

    @Test
    void testWithAnsiText_HappyPath() {
        AnsiText testee = new AnsiText(new MockScript());
        testee.addLine('Some ANSI text', AnsiColor.RED)
        testee.addLine('Some other ANSI text', AnsiColor.YELLOW)
        Assert.assertEquals(testee.getText(), '\u001B[31mSome ANSI text\u001B[m\n\u001B[33mSome other ANSI text\u001B[m\n')
    }
}