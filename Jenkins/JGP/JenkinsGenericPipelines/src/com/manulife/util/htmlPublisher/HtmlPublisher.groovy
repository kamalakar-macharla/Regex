package com.manulife.util.htmlpublisher

/**
 *
 *
 *
 **/
class HtmlPublisher implements Serializable {
    Script scriptObj
    Properties pipelineParams

    HtmlPublisher(Script scriptObj, Properties pipelineParams) {
        this.scriptObj = scriptObj
        this.pipelineParams = pipelineParams
    }

    def publish(def reportType = 'html') {
        scriptObj.logger.info('Publishing Html Report...')

        if ('html' == reportType) {
            if (null == pipelineParams.getProperty('htmlReportNames')
                || null == pipelineParams.getProperty('htmlReportFiles')
                || null == pipelineParams.getProperty('htmlReportRelativePaths')) {
                scriptObj.logger.debug(pipelineParams.getProperty('htmlReportNames') + ' ' +
                                       pipelineParams.getProperty('htmlReportFiles') + ' ' +
                                       pipelineParams.getProperty('htmlReportRelativePaths'))
                scriptObj.logger.info('Nothing to be published...')
                return
            }

            def names = pipelineParams.getProperty('htmlReportNames').tokenize('|')
            def files = pipelineParams.getProperty('htmlReportFiles').tokenize('|')
            def relativePaths = pipelineParams.getProperty('htmlReportRelativePaths').tokenize('|')

            int minimumSize = Math.min(relativePaths.size(), Math.min(names.size(), files.size()))
            def counter = 0
            while (counter < minimumSize) {
                scriptObj.logger.debug("Publishing name: ${names[counter]} file: ${files[counter]} Path: ${relativePaths[counter]}")
                publishHtml(names[counter], files[counter], relativePaths[counter])
                scriptObj.logger.info(names[counter] + ' --- has been published')
                counter++
            }
        }
    }

    private void publishHtml(String name, String file, String path) {
        scriptObj.publishHTML(
            target: [
                allowMissing         : false,
                alwaysLinkToLastBuild: false,
                keepAll              : true,
                reportDir            : path, // example - 'target/site/serenity'
                reportFiles          : file, // example - 'index.html'
                reportName           : name // example - "BDD Serenity Report"
            ]
        )
    }
}
