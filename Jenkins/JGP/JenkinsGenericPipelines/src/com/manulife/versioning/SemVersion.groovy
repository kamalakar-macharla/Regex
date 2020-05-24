package com.manulife.versioning

import com.cloudbees.groovy.cps.NonCPS

class SemVersion implements Serializable {
    private final int majorVersion
    private final int minorVersion
    private final int patchVersion
    private final String suffix

    SemVersion(int majorVersion, int minorVersion, int patchVersion, String suffix) {
        this.majorVersion = majorVersion
        this.minorVersion = minorVersion
        this.patchVersion = patchVersion
        this.suffix = suffix
    }

    static SemVersion parse(Script scriptObj, String versionString) {
        scriptObj.logger.debug("Attempting to create SemVersion object from: ${versionString}")

        String[] mainBits = versionString.tokenize('-')

        // Handle the major.minor.patch portion of the version number
        String[] versionBits = mainBits[0].tokenize('.')

        // Check that we have 3 version pieces (major.minor.patch)
        if (versionBits.size() < 3) {
            scriptObj.logger.error("The current project version: ${versionString} isn't a valid semver version.")
            throw new SemVersionException("The current project version: ${versionString} isn't a valid semver version.")
        }

        // Check that each element is an integer
        if (!versionBits[0].isInteger()) {
            scriptObj.logger.error("The project's major version is not an integer: ${versionBits[0]}")
            throw new SemVersionException("The project's major version is not an integer: ${versionBits[0]}")
        }

        if (!versionBits[1].isInteger()) {
            scriptObj.logger.error("The project's minor version is not an integer: ${versionBits[1]}")
            throw new SemVersionException("The project's minor version is not an integer: ${versionBits[1]}")
        }

        if (!versionBits[2].isInteger()) {
            scriptObj.logger.error("The project's patch version is not an integer: ${versionBits[2]}")
            throw new SemVersionException("The project's patch version is not an integer: ${versionBits[2]}")
        }

        // The version is valid
                // Extract the version suffix if there is one (such as SNAPSHOP, alpha, beta, ...)
        String suffix
        if (mainBits.size() > 1) {
            suffix = mainBits[1]
        }

        int major = (versionBits[0] as Integer)
        int minor = (versionBits[1] as Integer)
        int patch = (versionBits[2] as Integer)

        return new SemVersion(major, minor, patch, suffix)
    }

    @NonCPS
    String toString() {
        String retVal = "${majorVersion}.${minorVersion}.${patchVersion}"

        if (suffix) {
            retVal += "-${suffix}"
        }

        return retVal
    }

    SemVersion getReleaseVersion() {
        return new SemVersion(this.majorVersion,
                              this.minorVersion,
                              this.patchVersion,
                              null)
    }

    SemVersion getNextMajorVersion() {
        return new SemVersion(this.majorVersion + 1,
                              0,
                              0,
                              this.suffix)
    }

    SemVersion getNextMinorVersion() {
        return new SemVersion(this.majorVersion,
                              this.minorVersion + 1,
                              0,
                              this.suffix)
    }

    SemVersion getNextPatchVersion() {
        return new SemVersion(this.majorVersion,
                              this.minorVersion,
                              this.patchVersion + 1,
                              this.suffix)
    }
}