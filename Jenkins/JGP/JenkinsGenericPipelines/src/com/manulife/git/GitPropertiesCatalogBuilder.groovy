package com.manulife.git

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Populates the properties catalog with the properties related to GitLab.
 *
 */
class GitPropertiesCatalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('gitFlowType', 'Defaulting to GITFLOW.  This is currently the only value supported', 'GITFLOW')
    }
}
