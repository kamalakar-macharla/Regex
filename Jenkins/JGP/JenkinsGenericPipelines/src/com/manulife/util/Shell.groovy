package com.manulife.util

// vim: et:ts=4:sts=4:sw=4:fileencoding=utf-8

import com.manulife.logger.Level

/**
 *
 * Helper class for Shell commands.
 *
 **/
class Shell {
    final static String CA_FILE = 'blackduck-zscaler.pem'
    final static String CA_FILE_RESOURCE = 'com/manulife/ssl/zscaler-ca.pem'

    static String quickShell(Script scriptObj,
                             String command,
                             Boolean unix = null,
                             Boolean nativeCommand = true,
                             Boolean swallowOutput = false,
                             Level loggingLevel = Level.INFO ) {
        // Rely on boxing and unboxing instead of new Boolean() and booleanValue() which do not show in the whitelist,
        // https://github.com/jenkinsci/script-security-plugin/blob/master/src/main/resources/org/jenkinsci/plugins/scriptsecurity/sandbox/whitelists/generic-whitelist
        def onUnix = unix
        if (onUnix == null) {
            onUnix = scriptObj.isUnix()
        }

        String output
        if (onUnix) {
            output = scriptObj.sh(returnStdout: true, script: command)
        }
        else {
            if (nativeCommand) {
                scriptObj.logger.log(loggingLevel, "+ @${command}")
                output = Strings.removeCarriageReturns(scriptObj.bat(returnStdout: true,
                    script: Strings.escapeForCmdBatch('@' + command)))
            }
            else {
                // Use an environment variable CYGBINSLASH with a path to
                // Cygwin's bin directory followed by a backslash (if this was
                // not in PATH),
                //      c:\cygwin64\bin\
                def cygbinslash = scriptObj.env.CYGBINSLASH ?: ''
                // Expect quick commands in the following format,
                // POSIXCMD DRIVERFUL_OR_RELATIVE_WINDOWS_OR_POSIX_PATH... \
                //          2> /dev/null [ | POSIXCMD ...]... [|| :]
                def cygwinCommand = '@' + cygbinslash + (command
                    .replace('\\', '/')
                    .replace(' | ', " | ${cygbinslash}")
                    .replace('/dev/null', 'nul')
                    .replace('|| :', '|| ver>nul'))
                scriptObj.logger.log(loggingLevel, "+ ${cygwinCommand}")
                output = Strings.removeCarriageReturns(scriptObj.bat(returnStdout: true,
                                                                     script: Strings.escapeForCmdBatch(cygwinCommand)))
            }
        }

        if (!swallowOutput) {
            scriptObj.logger.log(loggingLevel, output)
        }
        return output
    }

    /**
     * Obtain a Java property such as java.home or user.home from the Java on PATH.
     * One of the BlackDuck hub-detect install stages uses Java from PATH.
     */
    static String getBuildJavaProperty(Script scriptObj, String propertyName, Boolean unix = null) {
        String javaOutput = quickShell(scriptObj, 'java -XshowSettings:properties 2>&1 || :', unix, true, false, Level.DEBUG).trim()
        String propertyNameRegEx = propertyName.replace('.', '\\.')

        for (def line in javaOutput.split('\n')) {
            line = line.trim()
            if (!line) {
                continue
            }

            def m = Strings.match(line, /${propertyNameRegEx} = (.*)/)
            if (m) {
                return m[0][1]
            }
        }

        return null
    }

    /**
     * Determine the platform and its file and path separators.
     */
    static getPlatformSpecifics(Script scriptObj, Boolean unix = null) {
        def onUnix = unix
        if (onUnix == null) {
            onUnix = scriptObj.isUnix()
        }

        return (onUnix) ? [onUnix, '/', ':'] : [onUnix, '\\', ';']
    }

    /**
     * Check and enable a Certification Authority in
     * ${javaHome}/jre/lib/security/cacerts or
     * ${javaHome}/lib/security/cacerts.
     */
    static checkEnableCertAuthority(Script scriptObj,
                                    String javaHome,
                                    String caFile,
                                    String alias,
                                    String resource,
                                    Boolean unix = null) {
        String fsep, psep
        (unix, fsep, psep) = getPlatformSpecifics(scriptObj, unix)

        def theJavaHome = javaHome
        if (javaHome == null) {
            theJavaHome = fixAndPropagateJavaHome(scriptObj, unix)
        }

        String jreHome = "${theJavaHome}${fsep}jre"
        if (!scriptObj.fileExists(jreHome)) {
            jreHome = theJavaHome
        }

        String caStorePath = "${jreHome}${fsep}lib${fsep}security${fsep}cacerts"
        if (!scriptObj.fileExists(caStorePath)) {
            scriptObj.logger.warning("Missing a Certification Authorities store in \"${caStorePath}\"")
            return false
        }

        String kt = ("\"${jreHome}${fsep}bin${fsep}keytool\" -keystore \"${caStorePath}\" " +
            '-keypass changeit -storepass changeit')
        String checkCertOutput = quickShell(scriptObj, "${kt} -list -alias \"${alias}\" 2>&1 || :", unix, true, false, Level.DEBUG).trim()

        if (checkCertOutput ==~ /(?s).* does not exist.*/) {
            // https://github.com/jenkinsci/workflow-cps-global-lib-plugin/commit/e03488893cdeabe4738a443a0bfadd5306c46b73
            String caBundle = Strings.deBOM(scriptObj.libraryResource(resource: resource, encoding: 'UTF-8'))
            String caPath = "${scriptObj.pwd(tmp: true)}${fsep}${caFile}"
            scriptObj.writeFile(file: caPath, text: caBundle, encoding: 'UTF-8')
            String addCertCommand = "${kt} -import -file \"${caPath}\" -alias \"${alias}\" -v -noprompt 2>&1 || :"
            String addCertWithAdminCommand

            if (unix) {
                addCertWithAdminCommand = "sudo ${addCertCommand}"
            }
            else {
                // TODO: consider running an elevated privilege command with "start -runas"
                //  https://stack.manulife.io/questions/1482/my-account-has-a-local-admin-right-on-a-windows-machine-but-privileged-commands/1483#1483
                // or logging into a privileged account.
                addCertWithAdminCommand = addCertCommand
            }

            String addCertOutput = quickShell(scriptObj, addCertWithAdminCommand, unix, true, false, Level.DEBUG).trim()
            if (unix && (addCertOutput ==~ /(?s)sudo: .*/)) {
                scriptObj.logger.debug('Trying the regular privilege after a failure to elevate it')
                addCertOutput = quickShell(scriptObj, addCertCommand, unix, true, false, Level.DEBUG).trim()
            }

            if (addCertOutput ==~ /(?s).*Exception:.*/) {
                scriptObj.logger.warning('Continuing on without the additional CA due to lack of privilege or a keytool error.')
                return false
            }
        }

        return true
    }

    /**
     * Check if ${JAVA_HOME}/jre exists in the file system.  Ignore it if it
     * came from a misconfigured Jenkins agent.
     * <p>
     * If JAVA_HOME is not set or misconfigured, find it using the java command
     * on PATH and set it one directory above jre.
     * <p>
     * Propagate JAVA_HOME to other environment variables such as BDS_JAVA_HOME
     * (for BlackDuck).
     */
    static String fixAndPropagateJavaHome(Script scriptObj, Boolean unix = null) {
        String fsep, psep
        (unix, fsep, psep) = getPlatformSpecifics(scriptObj, unix)

        String javaHome = scriptObj.env.JAVA_HOME
        if (javaHome != null) {
            if (!scriptObj.fileExists("${javaHome}${fsep}jre${fsep}lib${fsep}security${fsep}cacerts")) {
                if (!scriptObj.fileExists("${javaHome}${fsep}lib${fsep}security${fsep}cacerts")) {
                    scriptObj.logger.error("JAVA_HOME \"${javaHome}\" does not point to a JDK or a JRE directory.")
                    javaHome = null
                }
            }
        }

        String autodetected
        if (javaHome == null) {
            scriptObj.logger.debug('Looking for java\'s java.home...')
            javaHome = getBuildJavaProperty(scriptObj, 'java.home', unix)
            if (javaHome.endsWith('/jre') || javaHome.endsWith('\\jre')) {
                // Snip the last 4 characters "/jre" or "\jre".
                //
                // In a slice the upper boundary points to the last character
                // before the snipped contents.
                //
                // In a String.substring(beginIndex, endIndex) the end index
                // points to the beginning of the snipped contents.
                javaHome = javaHome[0..- 1 - '/jre'.size()]
            }
            autodetected = 'detected by running java'
        }
        else {
            autodetected = 'supplied by JAVA_HOME'
        }

        scriptObj.logger.debug("Setting JAVA_HOME and BDS_JAVA_HOME to the location ${autodetected}, \"${javaHome}\"")
        scriptObj.env.JAVA_HOME = javaHome
        scriptObj.env.BDS_JAVA_HOME = javaHome
        return javaHome
    }

    static String propagateAndroidSDKHome(Script scriptObj, String androidSdkHome = null) {
        scriptObj.env.ANDROID_HOME = (androidSdkHome != null) ? androidSdkHome : "${scriptObj.env.HOME}/Library/Android/sdk"
        return scriptObj.env.ANDROID_HOME
    }

    /**
     * Inject the Zscaler CA into ${JAVA_HOME}/jre/lib/security/cacerts or
     * ${JAVA_HOME}/lib/security/cacerts.
     */
    static void trustZscalerInJava(Script scriptObj, Boolean unix = null) {
        checkEnableCertAuthority(scriptObj,
                                 scriptObj.env.JAVA_HOME,
                                 CA_FILE,
                                 'zscaler',
                                 CA_FILE_RESOURCE,
                                 unix)
    }
}
