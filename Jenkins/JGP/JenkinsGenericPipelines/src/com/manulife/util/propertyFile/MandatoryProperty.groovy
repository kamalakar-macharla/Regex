package com.manulife.util.propertyfile

/**
 * Property that must be provided in the .properties files
 **/
class MandatoryProperty implements Serializable {
    String name
    String missingMessage
    boolean mandatory = true

    MandatoryProperty(String name, String missingMessage) {
        this.name = name
        this.missingMessage = missingMessage
    }
}
