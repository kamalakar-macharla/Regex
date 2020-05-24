package com.manulife.fortify

import com.manulife.pipeline.PipelineType
import com.manulife.util.propertyfile.PropertiesCatalog

import org.testng.annotations.Test
import org.testng.Assert

/**
 *
 * Populates the properties catalog with the properties for Fortity scanning.
 *
 **/
class FortifyPropertiesCalalogBuilderTest {
    @Test
    void testBuild_AEM_Maven_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_DotNet_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_DotNetCore_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_JavaMaven_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_MAVEN)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_JavaGradle_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_Nifi_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NIFI)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_NodeJS_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_Python_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_ShellExec_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }

    @Test
    void testBuild_SSRS_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SSRS)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 1)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
    }

    @Test
    void testBuild_Swift_Pipeline() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        FortifyPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)

        Assert.assertEquals(propertiesCatalog.getPropertyDefinitions().size(), 9)
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTriggers'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyApp'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyAppDescr'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyGating'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScriptWeb'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyServer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyTokenName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyVer'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('fortifyScanTree'))
    }
}
