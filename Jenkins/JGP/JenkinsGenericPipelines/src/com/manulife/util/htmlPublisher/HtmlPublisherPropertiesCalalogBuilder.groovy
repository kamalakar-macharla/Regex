package com.manulife.util.htmlpublisher

import com.manulife.util.propertyfile.PropertiesCatalog

/**
 *
 * Responsible to populate the properties catalog with the properties required by HtmlPublisher.
 *
 **/
class HtmlPublisherPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog) {
        propertiesCatalog.addOptionalProperty('htmlReportNames',
                                              'The list of names you want to tag your report in Jenkins seperated by \'|\'. Could be any name you want. Defaulting htmlReportNames to null.',
                                              null)
        propertiesCatalog.addOptionalProperty('htmlReportFiles',
                                              'The list of files of the reports generated seperated by \'|\'. Could be index.html for most framework. Defaulting htmlReportFiles to null.',
                                              null)
        propertiesCatalog.addOptionalProperty('htmlReportRelativePaths',
                                              'The list of relative path to the project root seperated by \'|\'. ' +
                                                'Could be serenity/report for serenity framework. Defaulting htmlReportRelativePaths to null.',
                                              null)
    }
}
