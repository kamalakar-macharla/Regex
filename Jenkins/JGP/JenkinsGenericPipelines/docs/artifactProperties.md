## Introduction
This page describes all the meta data that is captured and pushed with an artifact to Artifactory. The data can then be used to trace the the artifacts lifecycle.

| property name | language specific | description | 
| ------------- | ------------- | ------------- | 
| git.vcs.revision | All | Git commit Id when the CI pipeline ran to build the artifact        | 
| properties.hubVersionDist |      All     | Blackduck property to specify how this app is distributed.       | 
| properties.hubVersionPhase |     All     | BlackDuck property to describe the phase of the application     | 
| properties.hubExclusionPattern | All | Blackduck file exclusion pattern used in the CI pipeline      |  
| properties.releaseRepo | Maven | Name of the Artifactory virtual repository to resolve the project dependencies and to upload the modules produced by the job     |
| properties.snapshotRepo | Maven | Name of the Maven snapshot Artifactory repository to resolve and upload non-release Maven builds     |  
| gating.CodeQualityGateEnabled | All |  Specifies whether the SonarQube quality gate was used in the CI pipeline     |  
| gating.OpenSourceGovernanceGateEnabled | All | Specifies whether the Snyk or BlackDuck gate was used in the CI pipeline     |  
| gating.CodeQuality | All | States the result of the Sonar scan     |  
| gating.OpenSourceGovernance | All | States the result of the Snyk or BlackDuck scan     |  
| gating.CodeSecurity | All | States the result of the Fortify scan     |  