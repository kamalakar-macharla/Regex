package com.manulife.fortify

import com.manulife.pipeline.PipelineType
import com.manulife.util.Conditions
import com.manulife.util.propertyfile.PropertiesCatalog

/**
 *
 * Populates the properties catalog with the properties for Fortity scanning.
 *
 **/
class FortifyPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        // "the properties are added only for the pipeline types for which we implemented Fortify Scanning" -- frouel1
        propertiesCatalog.addOptionalProperty('fortifyTriggers', 'Defaulting fortifyTriggers property to null (meta-regex negation ' + Conditions.DEFAULT_TOOL_TRIGGERS, null)

        if (pipelineType in [ PipelineType.DOTNET,
                              PipelineType.DOTNETCORE,
                              PipelineType.JAVA_MAVEN,
                              PipelineType.JAVA_GRADLE,
                              PipelineType.AEM_MAVEN,
                              PipelineType.NODEJS,
                              PipelineType.SWIFT,
                              PipelineType.NIFI,
                              PipelineType.SHELLEXEC,
                              PipelineType.PYTHON]) {
            propertiesCatalog.addOptionalProperty('fortifyApp', 'Defaulting fortifyApp property to null (guessing from projectKey, sonarQubeProjectKey and JOB_BASE_NAME)', null)
            propertiesCatalog.addOptionalProperty('fortifyAppDescr', 'Defaulting fortifyAppDescr property to null (guessing from a URL of a git checkout)', null)
            propertiesCatalog.addOptionalProperty('fortifyGating', 'Defaulting fortifyGating property to true.', 'true')
            propertiesCatalog.addOptionalProperty('fortifyScriptWeb',
                    'Defaulting fortifyScriptWeb property to \"888,master,sample-builds/\"',
                    '888,master,sample-builds/')
            propertiesCatalog.addOptionalProperty('fortifyServer', 'Defaulting fortifyServer property to \"https://fortify.americas.manulife.net/ssc\"', 'https://fortify.americas.manulife.net/ssc')
            propertiesCatalog.addOptionalProperty('fortifyTokenName', 'Defaulting fortifyTokenName property to \"FORTIFY_MANAGE_APPLICATION_TOKEN\"', 'FORTIFY_MANAGE_APPLICATION_TOKEN')
            propertiesCatalog.addOptionalProperty('fortifyVer', 'Defaulting fortifyVer property to null (guessing from BRANCH_NAME)', null)
            propertiesCatalog.addOptionalProperty('fortifyScanTree', 'Defaulting fortifyScanTree property to null', null)
        }
    }
}
