package com.manulife.util.propertyfile

/**
 *
 * Responsible to read the JGP properties files.
 *
 **/
class PropertyFilesReader {
    static boolean read(Script scriptObj,
                        String propertiesFileName,
                        PropertiesCatalog propertiesCatalog,
                        String commonPropertiesFileName,
                        Properties properties) {
        scriptObj.logger.debug('************ Starting to process the Properties Files **************')
        Properties pipelineProperties = new Properties()

        readFile(scriptObj, commonPropertiesFileName, pipelineProperties)
        readFile(scriptObj, propertiesFileName, pipelineProperties)

        scriptObj.logger.debug('************ Validating the content of the Properties Files **************')
        PropertiesFileValidator propertiesFileValidator = new PropertiesFileValidator(propertiesCatalog)
        boolean valid = propertiesFileValidator.validateProperties(pipelineProperties)

        scriptObj.logger.info("Properties file content is valid? ${valid}\n\n\n")
        propertiesFileValidator.getReportDetails(scriptObj).printText()

        if (valid) {
            for (String name : pipelineProperties.stringPropertyNames()) {
               properties.setProperty(name, pipelineProperties.getProperty(name))
            }
        }

        scriptObj.logger.debug('************** Done processing the Properties Files ****************')

        return valid
    }

    static private readFile(Script scriptObj, def fileName, Properties pipelineProperties)  {
        def fileExists = scriptObj.fileExists "${scriptObj.env.WORKSPACE}/server/slx/jenkins/${fileName}"
        scriptObj.logger.info("${scriptObj.env.WORKSPACE}/server/slx/jenkins/${fileName} file found? " + fileExists)
        if (fileExists) {
            def fileProperties = scriptObj.readProperties file: "${scriptObj.env.WORKSPACE}/server/slx/jenkins/${fileName}"
            fileProperties.each { name, value ->
                pipelineProperties.setProperty(name, null != value ? value.trim() : value)
            }
        }
    }
}