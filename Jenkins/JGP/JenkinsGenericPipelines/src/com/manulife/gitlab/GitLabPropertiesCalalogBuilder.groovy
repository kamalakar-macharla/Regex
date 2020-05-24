package com.manulife.gitlab

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Populates the properties catalog with the properties related to GitLab.
 *
 */
class GitLabPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('gitLabAPITokenName', 'Defaulting gitLabAPITokenName property to \"GitLabApiTokenText\"', 'GitLabApiTokenText')
        propertiesCatalog.addOptionalProperty('gitLabSSHCredentialsId', 'Defaulting gitLabSSHCredentialsId property to \"GitLabSSH\"', 'GitLabSSH')
        propertiesCatalog.addOptionalProperty('gitLabEnableNotifications', 'Defaulting gitLabEnableNotifications property to true', 'true')

        if (pipelineType == PipelineType.AEM_MAVEN) {
            propertiesCatalog.addOptionalProperty('gitJenkinsSSHCredentials', 'Defaulting gitJenkinsSSHCredentials property to null', null)
        }
    }
}
