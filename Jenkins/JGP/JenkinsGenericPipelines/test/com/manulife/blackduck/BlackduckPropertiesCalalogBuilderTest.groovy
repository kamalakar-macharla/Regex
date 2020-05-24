package com.manulife.blackduck

import com.manulife.pipeline.PipelineType
import com.manulife.util.propertyfile.PropertiesCatalog

import org.testng.annotations.Test
import org.testng.Assert

class BlackDuckPropertiesCalalogBuilderTest {

    @Test
    void test_build_Any_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        BlackduckPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubVersionDist'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubVersionPhase'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubExcludedModules'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubExclusionPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubFailOnSeverities'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubFailPipelineOnFailedOpenSourceGovernance'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubTimeoutMinutes'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('hubUserPasswordTokenName'))
    }
}