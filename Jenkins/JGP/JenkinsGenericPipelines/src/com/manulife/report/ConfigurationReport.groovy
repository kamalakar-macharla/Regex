package com.manulife.report

import com.manulife.util.AnsiColor
import com.manulife.util.AnsiText
import com.manulife.jenkins.Nodes

/**
  *
  *  This class prints a report showing the configuration values (from Jenkinsfile) for this pipeline run and
  *   verifies that:
  *      1) The agent label is appropriate
  *      2) There isn't an issue in the branch regex.  Many people still use something similar to ^[dev].* which
  *          will match any branch name that contains a 'd', 'e' or 'v' character...
  *
 **/
class ConfigurationReport {
    private static final SEPARATOR = '*******************************************'

    private final Script scriptObj
    private final configuration

    ConfigurationReport(Script scriptObj, def configuration) {
        this.scriptObj = scriptObj
        this.configuration = configuration
    }

    void print() {
        try {
            AnsiText ansiText = new AnsiText(scriptObj)
            ansiText.addLine(SEPARATOR)
            ansiText.addLine('*******  Jenkinsfile Configuration  *******')
            ansiText.addLine(SEPARATOR)

            for (entry in configuration) {
                if (entry.key == 'jenkinsJobInitialAgent' && !Nodes.isValidNodeLabel(entry.value)) {
                    ansiText.addLine("  ${entry.key} = ${entry.value}.  Consider using one of ${Nodes.getNodeLabels()} instead.", AnsiColor.YELLOW)
                }
                else if (entry.key == 'jenkinsJobSecretToken') {
                    ansiText.addLine("  ${entry.key} = ********")
                }
                else if (entry.key == 'jenkinsJobRegEx' && entry.value.contains('[')) {
                    ansiText.addLine("  ${entry.key} = ${entry.value}.  [] such as in ^[dev] should not be used in regex since it will match any branch name that contains any of those charaters.",
                                     AnsiColor.YELLOW)
                }
                else {
                    ansiText.addLine("  ${entry.key} = ${entry.value}")
                }
            }

            ansiText.addLine(SEPARATOR)
            ansiText.printText()
        }
        catch (e) {
            scriptObj.echo("Exception while printing the ConfigurationReport.  Message: ${e}")
        }
    }
}