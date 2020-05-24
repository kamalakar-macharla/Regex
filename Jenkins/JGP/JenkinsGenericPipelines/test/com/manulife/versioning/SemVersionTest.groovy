package com.manulife.versioning

import com.manulife.logger.Level
import com.manulife.logger.Logger
import org.testng.annotations.Test
import org.testng.Assert

class SemVersionTest {
    static class MockScript extends Script {
        def logger = new Logger(this, Level.INFO)

        Object run() {
            println("Running...")
        }

        void ansiColor(aString, aLogger) {

        }
    }

    @Test
    void test_isValidVersion_ValidInput_SnapshotVersion() {
        try {
            SemVersion.parse(new MockScript(), "1.3.0-SNAPSHOT")
        }
        catch (SemVersionException e) {
            Assert.failed('Did not expect an exception: ${e}')
        }
    }

    @Test
    void test_isValidVersion_ValidInput_NotSnapshotVersion() {
        try {
            SemVersion.parse(new MockScript(), "1.3.0")
        }
        catch (SemVersionException e) {
            Assert.failed('Did not expect an exception: ${e}')
        }
    }

    @Test
    void test_isValidVersion_InvalidInput_MissingPatchVersion() {
        try {
            SemVersion.parse(new MockScript(), "1.3")
        }
        catch (SemVersionException e) {
            Assert.assertEquals(e.message, "The current project version: 1.3 isn't a valid semver version.")
            return
        }

        // Should not get there...
        Assert.failed('Should have thrown an exception')
    }

    @Test
    void test_isValidVersion_InvalidInput_MajorVersionNotInteger() {
        try {
            SemVersion.parse(new MockScript(), "a.3.0")
        }
        catch (SemVersionException e) {
            Assert.assertEquals(e.message, "The project's major version is not an integer: a")
            return
        }

        // Should not get there...
        Assert.failed('Should have thrown an exception')
    }

    @Test
    void test_isValidVersion_InvalidInput_MinorVersionNotInteger() {
        try {
            SemVersion.parse(new MockScript(), "1.a.0")
        }
        catch (SemVersionException e) {
            Assert.assertEquals(e.message, "The project's minor version is not an integer: a")
            return
        }

        // Should not get there...
        Assert.failed('Should have thrown an exception')
    }

    @Test
    void test_isValidVersion_InvalidInput_PatchVersionNotInteger() {
        try {
            SemVersion.parse(new MockScript(), "1.1.a")
        }
        catch (SemVersionException e) {
            Assert.assertEquals(e.message, "The project's patch version is not an integer: a")
            return
        }

        // Should not get there...
        Assert.failed('Should have thrown an exception')
    }

    @Test
    void test_getReleaseVersion_ValidInput_SnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.3.0-SNAPSHOT")
        testee = testee.getReleaseVersion()
        Assert.assertEquals(testee.toString(), "1.3.0")
    }

    @Test
    void test_getReleaseVersionAsString_ValidInput_NotSnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.2.0")
        testee = testee.getReleaseVersion()
        Assert.assertEquals(testee.toString(), "1.2.0")
    }

    @Test
    void test_toString_ValidInput_SnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.3.0-SNAPSHOT")
        Assert.assertEquals(testee.toString(), "1.3.0-SNAPSHOT")
    }

    @Test
    void test_toString_ValidInput_NotSnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.2.0")
        Assert.assertEquals(testee.toString(), "1.2.0")
    }

    @Test
    void test_getNextMinorVersion_ValidInput_SnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.3.0-SNAPSHOT")
        testee = testee.getNextMinorVersion()
        Assert.assertEquals(testee.toString(), "1.4.0-SNAPSHOT")
    }

    @Test
    void test_getNextMinorVersion_ValidInput_NotSnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.3.4")
        testee = testee.getNextMinorVersion()
        Assert.assertEquals(testee.toString(), "1.4.0")
    }

    @Test
    void test_getNextPatchVersion_ValidInput_SnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.2.0-SNAPSHOT")
        testee = testee.getNextPatchVersion()
        Assert.assertEquals(testee.toString(), "1.2.1-SNAPSHOT")
    }

    @Test
    void test_getNextPatchVersion_ValidInput_NotSnapshotVersion() {
        SemVersion testee = SemVersion.parse(new MockScript(), "1.2.0")
        testee = testee.getNextPatchVersion()
        Assert.assertEquals(testee.toString(), "1.2.1")
    }
}
