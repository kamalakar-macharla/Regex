package com.manulife.snyk

import com.manulife.pipeline.PipelineType
import com.manulife.util.propertyfile.PropertiesCatalog

/**
 * Populates the properties catalog with the properties related to Snyk
 **/
class SnykPropertiesCatalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        // TODO: Remove once we know Snyk works propertly

        propertiesCatalog.addOptionalProperty('snykGatingEnabled', 'Defaulting snykGatingEnabled property to \"true\"', 'true')
    }
}
