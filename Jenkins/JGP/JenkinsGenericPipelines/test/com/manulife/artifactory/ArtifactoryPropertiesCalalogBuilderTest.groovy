package com.manulife.artifactory

import com.manulife.pipeline.PipelineType
import com.manulife.util.propertyfile.PropertiesCatalog

import org.testng.annotations.Test
import org.testng.Assert

class ArtifactoryPropertiesCalalogBuilderTest {

    @Test
    void test_build_AEM_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.AEM_MAVEN)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_Docker_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOCKER)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_DotNet_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNET)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_DotNetCore_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DOTNETCORE)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_GoLang_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.GO)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_JavaGradle_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_GRADLE)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_JavaMaven_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_MAVEN)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_NodeJS_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_Swift_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SWIFT)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }

    @Test
    void test_build_Python_PipelineType() {
        PropertiesCatalog propertiesCatalog = new PropertiesCatalog()
        ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.PYTHON)

        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryDeploymentPattern'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('artifactoryInstance'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('artifactoryCredentialsId'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('projectDeliverableName'))
        Assert.assertNull(propertiesCatalog.getPropertyDefinition('snapshotRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseRepo'))
        Assert.assertNotNull(propertiesCatalog.getPropertyDefinition('releaseWriteRepo'))
    }
}