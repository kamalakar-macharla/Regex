package com.manulife.sonarqube

import com.manulife.versioning.SemVersion

/**
 *
 * Collection of utilities related to SonarQube
 *
 **/
class SonarQubeUtils {
    static boolean shouldPerformFullSonarQubeScanning(Script scriptObj, String branchName) {
        scriptObj.logger.debug("Calling shouldPerformFullSonarQubeScanning() with branchName = ${branchName}")
        return scriptObj.env.SONARQUBE_ACTIVE == 'TRUE'
    }

    static String getProjectVersion(SemVersion semVersion) {
        // In SonarQube we want to only use the major and minor portion
        //  of the project's real version number.  This is required
        //  because SonarQube's leak period is based on the project's
        //  version number.  We want to see all the issues of the current
        //  major.minor version in the leak period.

        return "${semVersion.majorVersion}.${semVersion.minorVersion}"
    }
}
