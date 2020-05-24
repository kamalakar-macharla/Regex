package com.manulife.util.propertyfile

import com.manulife.util.AnsiText

/**
 * Validates and fixes the properties provided to a pipeline (against what the pipeline defined as supported properties).
 **/
class PropertiesFileValidator implements Serializable {
    private Report report
    final private PropertiesCatalog catalog

    PropertiesFileValidator(PropertiesCatalog catalog) {
        this.catalog = catalog
    }

    boolean validateProperties(Properties properties) {
        report = new Report()
        boolean valid = true

        // Validate that all mandatory values are provided and default missing values when possible
        for (def propertyDefinition : catalog.propertyDefinitions) {
            String value = properties.getProperty(propertyDefinition.name)

            if (value == null || value.trim().isEmpty()) {
                if (propertyDefinition.mandatory) {
                    report.addEntry(propertyDefinition.name, Report.ReportEntryStatus.ERROR, '', '', propertyDefinition.missingMessage)
                    valid = false
                }
                else {  // Optional, set to default value
                    if (propertyDefinition.defaultValue != null) {
                        properties.setProperty(propertyDefinition.name, propertyDefinition.defaultValue)
                    }
                    report.addEntry(propertyDefinition.name, Report.ReportEntryStatus.OK, 'Default', propertyDefinition.defaultValue, propertyDefinition.missingMessage)
                }
            }
            else {
                if (!propertyDefinition.mandatory && ((value == null && propertyDefinition.defaultValue == null) || (value == propertyDefinition.defaultValue))) {
                    report.addEntry(propertyDefinition.name,
                                    Report.ReportEntryStatus.WARNING,
                                    'Properties Files', value,
                                    'Should remove entry from properties file since configured with same value as default.')
                }
                else {
                    report.addEntry(propertyDefinition.name, Report.ReportEntryStatus.OK, 'Properties Files', value, '')
                }
            }
        }

        // Identify the properties that were provided but are unknown by the catalog.  Either typo in property name or deprecated property
        for (String propertyName : properties.stringPropertyNames()) {
            if (!catalog.getPropertyDefinition(propertyName)) {
                report.addEntry(propertyName,
                                Report.ReportEntryStatus.WARNING,
                                'Properties Files',
                                'N/A',
                                'Property is unknown by this pipeline.  This is probably a deprecated property or there is a typo in the property name.')
            }
        }

        return valid
    }

    AnsiText getReportDetails(Script scriptObj) {
        return report.getReport(scriptObj)
    }
}
