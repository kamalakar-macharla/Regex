package com.manulife.util.propertyfile

/**
 * Contains the definition of all the properties supported by a pipeline.
 **/
class PropertiesCatalog implements Serializable {
    private final Map<String, Object> propertiesCatalog = new HashMap<String, Object>()

    void addMandatoryProperty(String name, String missingMessage) {
        propertiesCatalog[name] = new MandatoryProperty(name, missingMessage)
    }

    void addOptionalProperty(String name, String missingMessage, String defaultValue) {
        propertiesCatalog[name] = new OptionalProperty(name, missingMessage, defaultValue)
    }

    def getPropertyDefinition(String name) {
        return propertiesCatalog[name]
    }

    def getPropertyDefinitions() {
        return propertiesCatalog.values()
    }

    int size() {
        propertiesCatalog.size()
    }
}