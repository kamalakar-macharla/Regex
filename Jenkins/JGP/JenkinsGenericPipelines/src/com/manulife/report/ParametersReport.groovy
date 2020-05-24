package com.manulife.report

import com.manulife.util.AnsiText

/**
  *
  *  This class prints a report showing the parameter values for this pipeline run
  *
 **/
class ParametersReport {
    private static final SEPARATOR = '*******************************************'

    private final Script scriptObj
    private final params

    ParametersReport(Script scriptObj, def params) {
        this.scriptObj = scriptObj
        this.params = params
    }

    void print() {
        try {
            AnsiText ansiText = new AnsiText(scriptObj)
            ansiText.addLine(SEPARATOR)
            ansiText.addLine('**********  Pipeline Parameters  **********')
            ansiText.addLine(SEPARATOR)

            for (param in params) {
                ansiText.addLine("  ${param.key} = ${param.value}")
            }

            ansiText.addLine(SEPARATOR)
            ansiText.printText()
        }
        catch (e) {
            scriptObj.echo("Exception while printing the ParametersReport.  Message: ${e}.message")
        }
    }
}