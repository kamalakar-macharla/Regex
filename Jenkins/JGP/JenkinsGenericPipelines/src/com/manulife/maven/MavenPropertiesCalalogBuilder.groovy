package com.manulife.maven

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Responsible to populate the properties catalog with Maven related property definitions.
 *
 **/
class MavenPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('mavenBuildGoal', 'Defaulting mavenBuildGoal property to \"-T 2 -B clean compile\"', '-T 2 -B clean compile')
        propertiesCatalog.addOptionalProperty('mavenSettingsFileName', 'Defaulting mavenSettingsFileName property to \"settings.xml\"', 'settings.xml')
        propertiesCatalog.addOptionalProperty('mavenTestGoal',
                                              'Defaulting mavenTestGoal property to \"-B -f pom.xml test -Dmaven.test.failure.ignore=true\"',
                                              '-B -f pom.xml test -Dmaven.test.failure.ignore=true')

        if (pipelineType == PipelineType.JAVA_MAVEN_TEST) {
            propertiesCatalog.addMandatoryProperty('mavenIntegrationTestGoal', 'Missing mavenIntegrationTestGoal property value')
        }

        propertiesCatalog.addOptionalProperty('mavenPOMRelativeLocation', 'Defaulting mavenPOMRelativeLocation property to \"pom.xml\"', 'pom.xml')
    }
}
