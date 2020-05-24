package com.manulife.report

import com.manulife.pipeline.PipelineUtils
import com.manulife.util.AnsiText

/**
 *
 * Provides JGP users with information about how to get production support.
 *
 **/
class ProductionSupportInfo {
    private static final SEPARATOR = '**************************************************************************************'
    private final Script scriptObj

    ProductionSupportInfo(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    void print() {
        try {
            AnsiText ansiText = new AnsiText(scriptObj)
            ansiText.addLine(SEPARATOR)
            ansiText.addLine('************************************* Need Help? *************************************')
            ansiText.addLine(SEPARATOR)
            ansiText.addLine('  1) Make sure you review the official documentation here: https://git.platform.manulife.io/CDT_Common/JenkinsGenericPipelines/tree/master')
            ansiText.addLine('  2) Try to find the answer on the Manulife StackOverflow instance: https://stack.manulife.io')

            String jobName = PipelineUtils.getJobName(scriptObj)

            if (jobName.startsWith('MFC_')) {
                ansiText.addLine("  3) Couldn't find the solution? Open a ticket with our production support team: " +
                       'https://cpcnissgwp01.americas.manulife.net:23800/secure/CreateIssue.jspa?pid=20377&issuetype=3')
            }
            else if (jobName.startsWith('JH_')) {
                ansiText.addLine('''  3) Couldn't find the answer?  Reach out to 'JH_DEVOPS_PIPELINE_TEAM@jhancock.com' ''')
            }
            else if (jobName.startsWith('AP')) {
                ansiText.addLine('''  3) Reach out to Asia's DevOps team.''')
            }
            else if (jobName.startsWith('ETS') ||
                    jobName.startsWith('GF') ||
                    jobName.startsWith('GSD') ||
                    jobName.startsWith('GSPE')) {
                ansiText.addLine('''  3) Reach out to Global's DevOps team.''')
            }

            ansiText.addLine(SEPARATOR)
            ansiText.printText()
        }
        catch (e) {
            scriptObj.echo("Exception while printing the ProductionSupportInfo.  Message: ${e}.message")
        }
    }
}