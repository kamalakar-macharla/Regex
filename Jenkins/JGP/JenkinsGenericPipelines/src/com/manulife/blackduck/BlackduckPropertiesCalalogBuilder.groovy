package com.manulife.blackduck

import com.manulife.pipeline.PipelineType
import com.manulife.util.Conditions
import com.manulife.util.propertyfile.PropertiesCatalog

/**
 * Populates the properties catalog with the properties related to BlackDuck
 **/
class BlackduckPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addMandatoryProperty('hubVersionDist', 'Missing hubVersionDist property value.  Should be one of:  INTERNAL, EXTERNAL, SAAS, OPENSOURCE')
        propertiesCatalog.addMandatoryProperty('hubVersionPhase', 'Missing hubVersionPhase property value.  Should be one of: PLANNING, DEVELOPMENT, RELEASED, DEPRECATED, ARCHIVED')

        propertiesCatalog.addOptionalProperty('hubExcludedModules', 'Defaulting hubExcludedModules property to \"Nothing_To_Exclude\"', 'Nothing_To_Exclude')
        propertiesCatalog.addOptionalProperty('hubExclusionPattern', 'Defaulting hubExclusionPattern property to \"/Nothing/To/Exclude/\"', '/Nothing/To/Exclude/')
        propertiesCatalog.addOptionalProperty('hubFailOnSeverities', 'Defaulting hubFailOnSeverities to ALL', 'ALL')
        propertiesCatalog.addOptionalProperty('hubFailPipelineOnFailedOpenSourceGovernance', 'Defaulting hubFailPipelineOnFailedOpenSourceGovernance property to \"true\"', 'true')
        propertiesCatalog.addOptionalProperty('hubTimeoutMinutes', 'Defaulting hubTimeoutMinutes property to 9999', '9999')
        propertiesCatalog.addOptionalProperty('hubTriggers', 'Defaulting hubTriggers property to null (meta-regex negation ' + Conditions.DEFAULT_TOOL_TRIGGERS, null)
        propertiesCatalog.addOptionalProperty('hubUserPasswordTokenName', 'Defaulting hubUserPasswordTokenName property to \"jenkins_blackduck\"', 'jenkins_blackduck')
    }
}
