package com.manulife.sonarqube

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Responsible to populate properties catalog with properties required for SonarQube.
 *
 **/
class SonarQubePropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('sonarQubeFailPipelineOnFailedQualityGate', 'Defaulting sonarQubeFailPipelineOnFailedQualityGate property to true.', 'true')
        propertiesCatalog.addOptionalProperty('sonarQubeSources', 'Defaulting sonarQubeSources property to an empty string.', '')
        propertiesCatalog.addOptionalProperty('sonarQubeExclusions', 'Defaulting sonarQubeExclusions property to an empty string.', '')

        if (pipelineType == PipelineType.DOCKER) {
            propertiesCatalog.addOptionalProperty('sonarQubeProjectSettings', 'Defaulting sonarProjectSettings property to sonar-project.properties.', 'sonar-project.properties')
        }

        if (pipelineType == PipelineType.DOTNET) {
            propertiesCatalog.addMandatoryProperty('sonarQubeProjectVersion', 'Missing sonarQubeProjectVersion property.  Should be set to the project version number to be used in SonarQube.')
        }
    }
}
