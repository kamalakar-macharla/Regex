package com.manulife.artifactory

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * This class is responsible to populate the properties catalog with the properties related to Artifactory integration in the JGP.
 *
 **/
class ArtifactoryPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('artifactoryDeploymentPattern', 'Defaulting artifactoryDeploymentPattern property to null', null)
        propertiesCatalog.addOptionalProperty('artifactoryInstance', 'Defaulting artifactoryInstance property to \"Artifactory-Global-Prod\"', 'Artifactory-Global-Prod')

        if (pipelineType == PipelineType.DOTNET || pipelineType == PipelineType.DOTNETCORE) {
            propertiesCatalog.addMandatoryProperty('artifactoryCredentialsId',
                                                   'Missing artifactoryCredentialsId property value which must be set to the id of the credentials entry to be used to connect to Artifactory.')
            propertiesCatalog.addOptionalProperty('projectDeliverableName', 'Defaulting projectDeliverableName property to null', null)
        }

        if (pipelineType == PipelineType.JAVA_MAVEN || pipelineType == PipelineType.JAVA_GRADLE) {
            propertiesCatalog.addMandatoryProperty('snapshotRepo',
                                                   'Missing snapshotRepo property value.  Should be set to the Artifactory snapshot repo name.  An example would be mfc-gb-maven-snapshot.')
        }

        if (pipelineType == PipelineType.AEM_MAVEN ||
            pipelineType == PipelineType.DOTNET ||
            pipelineType == PipelineType.DOTNETCORE ||
            pipelineType == PipelineType.JAVA_GRADLE ||
            pipelineType == PipelineType.JAVA_MAVEN ||
            pipelineType == PipelineType.EDGE_DEPLOY ||
            pipelineType == PipelineType.PYTHON) {
            propertiesCatalog.addMandatoryProperty('releaseRepo', 'Missing releaseRepo property value.  Should be set to the Artifactory release repo name.  An example would be mfc-gb-nuget.')
            propertiesCatalog.addOptionalProperty('releaseWriteRepo', 'Defaulting releaseWriteRepo property to null (using releaseRepo)', null)
        }
    }
}
