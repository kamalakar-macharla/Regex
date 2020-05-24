package com.manulife.gating

import com.manulife.blackduck.BlackDuckResult
import com.manulife.fortify.FortifyResult
import com.manulife.snyk.SnykResult
import com.manulife.sonarqube.SonarQubeResult
import com.manulife.util.AnsiColor
import com.manulife.util.AnsiText

/**
  *
  * This class is responsible to produce an ANSI report that shows how the project does for all the gates.
  *
 **/
class GatingReport {
    static AnsiText getReport(Script scriptObj,
                              BlackDuckResult blackDuckResult,
                              SnykResult snykResult,
                              FortifyResult fortifyResult,
                              SonarQubeResult sonarQubeResult) {
        AnsiText report = new AnsiText(scriptObj)
        report.addLine('###########################################################################################################')
        report.addLine('######                                         GATING REPORT                                         ######')
        report.addLine('###########################################################################################################')
        if (sonarQubeResult != null) {
            AnsiColor color = AnsiColor.GREEN
            if (!sonarQubeResult.codeQualityGatePassed) {
                if (sonarQubeResult.message.contains('FAILED')) {
                    color = AnsiColor.RED
                }
                else {
                    color = AnsiColor.YELLOW
                }
            }
            report.addLine("Code Quality Gate: ${sonarQubeResult.message}", color)
        }
        if (fortifyResult != null) {
            AnsiColor color = AnsiColor.GREEN
            if (!fortifyResult.codeSecurityGatePassed) {
                if (fortifyResult.message.contains('FAILED')) {
                    color = AnsiColor.RED
                }
                else {
                    color = AnsiColor.YELLOW
                }
            }
            report.addLine("Code Security Gate: ${fortifyResult.message}", color)
        }
        if (blackDuckResult != null) {
            AnsiColor color = AnsiColor.GREEN
            if (!blackDuckResult.governanceGatePassed) {
                if (blackDuckResult.message.contains('FAILED')) {
                    color = AnsiColor.RED
                }
                else {
                    color = AnsiColor.YELLOW
                }
            }
            report.addLine("Open-Source Governance Gate (BlackDuck): ${blackDuckResult.message}", color)
        }
        if (snykResult != null) {
            AnsiColor color = AnsiColor.GREEN
            if (!snykResult.governanceGatePassed) {
                if (snykResult.message.contains('FAILED')) {
                    color = AnsiColor.RED
                }
                else {
                    color = AnsiColor.YELLOW
                }
            }
            report.addLine("Open-Source Governance Gate (Snyk): ${snykResult.message}", color)
        }
        report.addLine('###########################################################################################################')
        return report
    }
}