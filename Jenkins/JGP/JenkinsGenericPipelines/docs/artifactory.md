# Introduction

Artifactory is used by Manulife to:
 * Resolve project dependencies on both open-source libraries and Manulife libraries
 * Store Manulife binary assets with meta data. To see a complete list of the data captured it can be [viewed here](docs/artifactProperties.md) 

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | artifactoryInstance | Artifactory instance to use (defined in Jenkins config) | Artifactory-Global-Prod is the only value currently supported | Artifactory-Global-Prod |
| Yes | artifactoryCredentialsId | Id of the credentials entry to be used to connect to Artifactory.  The entry can be of a user name/password type with the encrypted (but not base64-encoded) password. | Artifactory-Credentials | |
| Yes | releaseRepo | Name of the Artifactory VIRTUAL repository to resolve the project dependencies and to upload the modules produced by the job.  Referring to a -local repo will prevent from using non-Manulife dependencies, possibly breaking the build. | mfc-BUSINESS\_UNIT-nuget or mfc-BUSINESS\_UNIT-maven-release | N/A |
| Yes | releaseWriteRepo | Name of the Artifactory repository that should be used to upload the application produced by the job. | mfc-BUSINESS\_UNIT-generic-snapshot-local | null (use releaseRepo) |
| Yes | snapshotRepo | Name of the Maven snapshot Artifactory repository to resolve and upload non-release Maven builds.  Referring to a -local repo will prevent from using non-Manulife dependecnies, possibly breaking the build. | mfc-BUSINESS\_UNIT-maven-snapshot | N/A |
| Yes | artifactoryDeploymentPattern | Defines what Jenkins should upload to Artifactory. WARNING: It is case sensitive, so \*.NuPkg won't work while \*.nupkg will. | Usually \*.jar, \*.nupkg, ... | |
| No  | projectDeliverableName | Name of the DotNet module or application produced by the project.  If not specified, the pipeline won't upload the binaries to Artifactory | | null |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

# Properties Applicability

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| artifactoryInstance          | o | o | o | o | o |
| artifactoryCredentialsId     |   | m |   |   |   |
| releaseRepo                  | m | m | m |   |   |
| releaseWriteRepo             | o | o | o |   |   |
| snapshotRepo                 |   |   | m |   |   |
| artifactoryDeploymentPattern | o | o | o | o | o |
| projectDeliverableName       |   | o |   |   |   |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
