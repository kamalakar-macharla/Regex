package com.manulife.sonarqube

// vim: et:ts=4:sts=4:sw=4:fileencoding=utf-8

import com.manulife.logger.Level
import com.manulife.util.AnsiColor
import com.manulife.util.AnsiText
import com.manulife.util.Shell
import com.manulife.util.Strings

/**
 *
 * Responsible to query SonarQube server for scan job result.
 *
 **/
class SonarQubeCodeQualityGate implements Serializable {
    final static int WAIT_IN_MINUTES = 5
    final static int PAUSE_IN_SECONDS = 5
    final static String CA_BUNDLE_FILE = 'sonar-zscaler-bundle.pem'
    final static String SONAR_STATUS_OUTPUT_FILE = 'sonarqube-status.json'
    final static String SONAR_ANALYSIS_STATUS_OUTPUT_FILE = 'sonarqube-analysis-status.json'

    static SonarQubeResult check(Script scriptObj) {
        String[] ceTaskResult = null
        SonarQubeResult sonarQubeResult = new SonarQubeResult()
        sonarQubeResult.message = 'The project wasn\'t scanned with SonarQube.'
        sonarQubeResult.sonarBlockerIssueCount = 0
        sonarQubeResult.sonarMajorIssueCount = 0
        sonarQubeResult.sonarCriticalIssueCount = 0
        sonarQubeResult.codeQualityGatePassed = false
        boolean pastTimeout = false

        try {
            def sonarQubeFileSearch = scriptObj.findFiles(glob: '**/report-task.txt')
            if (!sonarQubeFileSearch.length) {
                scriptObj.logger.error('SonarQube\'s result file **/report-task.txt not found.')
                sonarQubeResult.message = 'The project FAILED the Code Quality Gate! (report-task.txt not generated; check the Jenkins console log for sonar-runner\'s Report status)'
                return sonarQubeResult
            }

            String sonarQubeReport = scriptObj.readFile(file: sonarQubeFileSearch[0].path, encoding: 'UTF-8')
            def sonarQubeLines = sonarQubeReport.split('\n')
            String ceTaskUrl = null
            for (def line in sonarQubeLines) {
                line = line.trim()
                def m = Strings.match(line, /ceTaskUrl=(.*)/)
                /*
                    projectKey=devops:guild-demo
                    serverUrl=https://sonar.manulife.com
                    serverVersion=6.7.1.35068
                    dashboardUrl=https://sonar.manulife.com/dashboard/index/devops:guild-demo
                    ceTaskId=AWQVcdYDSdqkBI3n9ddI
                    ceTaskUrl=https://sonar.manulife.com/api/ce/task?id=AWQVcdYDSdqkBI3n9ddI
                */
                if (m) {
                    ceTaskUrl = m[0][1]
                    break
                }
            }

            if (!ceTaskUrl) {
                scriptObj.logger.error("SonarQube result file ${sonarQubeFileSearch[0].path} has no ceTaskUrl")
                return sonarQubeResult
            }

            if (!scriptObj.env.SONAR_TOKEN) {
                scriptObj.logger.debug("Missing a Jenkins credential ${EnvironmentVariablesInitializer.getSonarQubeTokenName(scriptObj.env.SONAR_ENVIRONMENT)} in SONAR_TOKEN")
                return sonarQubeResult
            }

            String token = scriptObj.env.SONAR_TOKEN
            scriptObj.timeout(time: WAIT_IN_MINUTES, unit: 'MINUTES') {
                // The waitForQualityGate method opens a Sonar Web Hook listener, which may be late for an incoming notification.
                // Also, using timeout() seems to switch executors, preventing the method from finding the sonar/report-task.txt file in the other executor's workspace.
                // https://github.com/SonarSource/sonar-scanner-jenkins/blob/20ab810d18810fcab33ea965916e38ca614c700a/src/main/java/org/sonarsource/scanner/jenkins/pipeline/WaitForQualityGateStep.java#L169
                // def qg = scriptObj.waitForQualityGate()
                ceTaskResult = pollForCETaskCompletion(scriptObj, token, ceTaskUrl)
            }
        }
        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException | hudson.AbortException e) {
            scriptObj.logger.warning('SonarQube execution was aborted by a job timeout'
                    + ' in the \"Jenkinsfile\" script or by a user.')
            sonarQubeResult.message = ('Project status UNKNOWN.  SonarQube was'
                    + ' aborted by a job timeout in the \"Jenkinsfile\" script or by a user.')
            // Swallow the abort exception, assuming it is sent to other threads as well.
            pastTimeout = true
        }

        if (ceTaskResult != null) {
            scriptObj.logger.debug("CE Task result: ${ceTaskResult[0]}")
            scriptObj.logger.debug("Analysis Id: ${ceTaskResult[1]}")
        }

        // Propagate unexpected exceptions, assuming that they are rare and that the JGP
        // developers may want to investigate the stack trace.

        // The plugin returns an 'OK' when the REST API returns 'SUCCESS'.
        if (ceTaskResult != null && (ceTaskResult[0] == 'SUCCESS' || ceTaskResult[0] == 'OK')) {
            // At this point we know the CE engine was able to analyze the project.  We now have to check the project's quality gate.
            String token = scriptObj.env.SONAR_TOKEN
            String analysisId = ceTaskResult[1]
            String[] qualityGateStatus = queryGatingStatus(scriptObj, token, analysisId)
            sonarQubeResult.sonarBlockerIssueCount = qualityGateStatus[1].toInteger()
            sonarQubeResult.sonarMajorIssueCount = qualityGateStatus[2].toInteger()
            sonarQubeResult.sonarCriticalIssueCount = qualityGateStatus[3].toInteger()

            if (qualityGateStatus[0]  == 'SUCCESS' || qualityGateStatus[0] == 'OK') {
                sonarQubeResult.message = 'The project PASSED the Code Quality Gate!'
                sonarQubeResult.codeQualityGatePassed = true
            }
            else if (qualityGateStatus[0]  == 'UNKNOWN') {
                sonarQubeResult.message = 'Current status UNKNOWN.  Unable to query SonarQube for code Quality Gate.'
            }
            else {
                sonarQubeResult.message = 'The project FAILED the Code Quality Gate!'
            }
        }
        else {
            if (pastTimeout) {
                sonarQubeResult.message = "The project took more than ${WAIT_IN_MINUTES} minutes to process in SonarQube \
or was cancelled, current status UNKNOWN."
            }
            else {
                sonarQubeResult.message = 'Current status UNKNOWN.  Unable to query SonarQube for Computation Engine results.'
            }
        }

        return sonarQubeResult
    }

    static String[] pollForCETaskCompletion(Script scriptObj, String token, String ceTaskUrl) {
        String tmpDir = scriptObj.pwd(tmp: true)
        String caBundlePath = "${tmpDir}/${CA_BUNDLE_FILE}"
        String sonarStatusPath = "${tmpDir}/${SONAR_STATUS_OUTPUT_FILE}"
        String command = "curl -s --cacert \"${caBundlePath}\" -u \"${token}:\" -o \"${sonarStatusPath}\" --write-out \"%{http_code}\" \"${ceTaskUrl}\""
        String caBundle = Strings.deBOM(scriptObj.libraryResource(resource: 'com/manulife/ssl/zscaler-curl-bundle.pem', encoding: 'UTF-8'))
        scriptObj.writeFile(file: caBundlePath, text: caBundle, encoding: 'UTF-8')

        int attempt = 0
        while (true) {
            attempt++
            scriptObj.logger.info("Querying SonarQube Server for CE Task Result.  Attempt #${attempt}")
            String httpCode = Shell.quickShell(scriptObj, command, null, false, false, Level.DEBUG).trim()
            if ('200' == httpCode) {
                String body = scriptObj.readFile(file: sonarStatusPath, encoding: 'UTF-8').trim()
                scriptObj.logger.debug(body)
                /*
                    --- /tmp/inprog.txt     2018-06-19 07:15:32.670068200 -0400
                    +++ /tmp/success.txt    2018-06-19 07:15:57.374468200 -0400
                    @@ -6,12 +6,15 @@
                         "componentKey": "MFC_Example_DotNetCore2_Microservice_PCF",
                         "componentName": "MFC_Example_DotNetCore2_Microservice_PCF",
                         "componentQualifier": "TRK",
                    -    "status": "IN_PROGRESS",
                    +    "analysisId": "AWQXu8-JcJ2bEIGp1AbX",
                    +    "status": "SUCCESS",
                         "submittedAt": "2018-06-19T07:08:59-0400",
                         "submitterLogin": "frouel1",
                         "startedAt": "2018-06-19T07:09:01-0400",
                    -    "executionTimeMs": 22203,
                    +    "executedAt": "2018-06-19T07:09:24-0400",
                    +    "executionTimeMs": 23075,
                         "logs": false,
                    +    "hasScannerContext": true,
                         "organization": "default-organization"
                       }
                     }
                */
                // JsonSlurper#parseText() returns a non-serializable LazyMap.
                // To avoid returning a non-serializable object, we return a String field.
                def data = scriptObj.readJSON text: "${body}"
                String status = data?.task?.status
                String analysisId = data?.task?.analysisId

                if ((status != null) && (status != 'PENDING') && (status != 'IN_PROGRESS')) {
                    scriptObj.logger.info("SonarQube Server returned the following result for Computation Engine Task: ${status}")
                    return [status, analysisId]
                }
            }

            scriptObj.sleep(PAUSE_IN_SECONDS)
        }
    }

    static String[] queryGatingStatus(Script scriptObj, String token, String analysisId) {
        String tmpDir = scriptObj.pwd(tmp: true)
        String caBundlePath = "${tmpDir}/${CA_BUNDLE_FILE}"
        String sonarStatusPath = "${tmpDir}/${SONAR_ANALYSIS_STATUS_OUTPUT_FILE}"
        String sonarQubeUrl = EnvironmentVariablesInitializer.getSonarQubeServerURL(scriptObj.env.SONAR_ENVIRONMENT)
        String command = "curl -s --cacert \"${caBundlePath}\" -u \"${token}:\" -o \"${sonarStatusPath}\" --write-out \"%{http_code}\" " +
                          "\"${sonarQubeUrl}/api/qualitygates/project_status?analysisId=${analysisId}\""
        String caBundle = Strings.deBOM(scriptObj.libraryResource(resource: 'com/manulife/ssl/zscaler-curl-bundle.pem', encoding: 'UTF-8'))
        scriptObj.writeFile(file: caBundlePath, text: caBundle, encoding: 'UTF-8')

        scriptObj.logger.info('Querying SonarQube Server for Code Quality Gate Result.')
        String httpCode = Shell.quickShell(scriptObj, command, null, false, false, Level.DEBUG).trim()
        if ('200' == httpCode) {
            String body = scriptObj.readFile(file: sonarStatusPath, encoding: 'UTF-8').trim()
            scriptObj.logger.debug(body)
            // {"projectStatus":{"status":"ERROR",
            //     "conditions":[{"status":"OK","metricKey":"duplicated_lines_density","comparator":"GT","periodIndex":1,"errorThreshold":"7","actualValue":"0.0"},
            //                   {"status":"OK","metricKey":"new_blocker_violations","comparator":"GT","periodIndex":1,"errorThreshold":"0","actualValue":"0"},
            //                   {"status":"ERROR","metricKey":"new_critical_violations","comparator":"GT","periodIndex":1,"errorThreshold":"0","actualValue":"1"},
            //                   {"status":"OK","metricKey":"test_errors","comparator":"GT","periodIndex":1,"errorThreshold":"0","actualValue":"0"},
            //                   {"status":"OK","metricKey":"test_failures","comparator":"GT","periodIndex":1,"errorThreshold":"0","actualValue":"0"},
            //                   {"status":"OK","metricKey":"new_sqale_debt_ratio","comparator":"GT","periodIndex":1,"errorThreshold":"7","actualValue":"0.0"},
            //                   {"status":"OK","metricKey":"new_major_violations","comparator":"GT","periodIndex":1,"errorThreshold":"5","actualValue":"0"}],
            //     "periods":[{"index":1,"mode":"previous_version","date":"2018-11-20T11:22:29-0500"}],"ignoredConditions":false}}
            // JsonSlurper#parseText() returns a non-serializable LazyMap.
            // To avoid returning a non-serializable object, we return a String field.
            def data = scriptObj.readJSON text: "${body}"
            String status = data?.projectStatus?.status

            def conditions = data?.projectStatus?.conditions
            def newBlocker = 0
            def newCritical = 0
            def newMajor = 0

            AnsiText report = new AnsiText(scriptObj)
            report.addLine('==========================================================')
            report.addLine('Code Quality Gate Criteria:')
            report.addLine('==========================================================')
            for (condition in conditions) {
                AnsiColor color = AnsiColor.GREEN
                if (condition.status != 'OK') {
                    color = AnsiColor.RED
                }

                if (condition.metricKey == 'new_blocker_violations') {
                    newBlocker = condition.actualValue
                }

                if (condition.metricKey == 'new_critical_violations') {
                    newCritical = condition.actualValue
                }

                if (condition.metricKey == 'new_major_violations') {
                    newMajor = condition.actualValue
                }

                report.addLine("${condition.metricKey} ${condition.comparator} ${condition.errorThreshold}?  Actual value: ${condition.actualValue}", color)
            }
            report.addLine('==========================================================')

            report.printText()

            return [status, newBlocker, newCritical, newMajor]
        }

        scriptObj.logger.error("Unable to query SonarQube for project's code quality gate status.  Got error: ${httpCode}")
        String body = scriptObj.readFile(file: sonarStatusPath, encoding: 'UTF-8').trim()
        scriptObj.logger.error(body)
        return ['UNKNOWN']
    }
}
