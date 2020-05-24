package com.manulife.util.propertyfile

/**
 * Property that MAY be provided in the .properties files.
 * If not provided, the pipeline will use the default value defined on this class.
 **/
class OptionalProperty implements Serializable {
    String name
    String missingMessage
    String defaultValue
    boolean mandatory

    OptionalProperty(String name, String missingMessage, String defaultValue) {
        this.name = name
        this.missingMessage = missingMessage
        this.defaultValue = defaultValue
    }
}