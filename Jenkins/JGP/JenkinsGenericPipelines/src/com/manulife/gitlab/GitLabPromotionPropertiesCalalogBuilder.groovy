package com.manulife.gitlab

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Populates the properties catalog with the properties related to GitLab code promotions.
 *
 **/
class GitLabPromotionPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addMandatoryProperty('fromBranch', '[ERROR] fromBranch property is mandatory.  Must contain the name of the branch the pipeline will promote code from')
        propertiesCatalog.addMandatoryProperty('gitLabSSHCredentialsId',
                                               '[ERROR] gitLabSSHCredentialsId property is mandatory.  Must contain the name of the GitLab SSH crdentials entry in the Jenkins credentials vault')
        propertiesCatalog.addMandatoryProperty('toBranch', '[ERROR] toBranch property is mandatory.  Must contain the name of the branch the pipeline will promote code to')
        propertiesCatalog.addOptionalProperty('fromSnaphotToReleaseOnToBranch',
                                              'Defaulting fromSnaphotToReleaseOnToBranch to false.  Set to true if you want the promotion to increment the patch version.', 'false')
        propertiesCatalog.addOptionalProperty('increaseFromBranchMinorVersion',
                                              'Defaulting increaseFromBranchMinorVersion to false.  Set to true if you want the promotion to increment the minor version.', 'false')
        propertiesCatalog.addOptionalProperty('increaseToBranchPatchVersion',
                                              'Defaulting increaseToBranchPatchVersion to false.  Set to true if you want the promotion to increment the patch version.', 'false')
        propertiesCatalog.addOptionalProperty('onlyOneReleaseBranch',
                                              'Defaulting onlyOneReleaseBranch to false.  Set to true if your project should have only one release branch.', 'false')
    }
}
