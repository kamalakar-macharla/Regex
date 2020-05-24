# Introduction

GitLab is the standard Source Code Management tool (code versioning) at Manulife.

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | gitJenkinsSSHCredentials | SSH Credentials to use git client on GitLab repo |  |  |
| Yes | onlyOneReleaseBranch | Indicates if that project is managed with one release branch or many |  | false |
| No | fromBranch | Name of the branch promoting from |  |  |
| No | toBranch | Name of the branch promoting to |  |  |
| No | increaseFromBranchMinorVersion | Increase the minor version in the branch the code is promoted from? |  | false |
| No | increaseToBranchPatchVersion | Increase the patch version in the branch the code is promoted to? |  | false |
| No | fromSnaphotToReleaseOnToBranch | Should the "to" version be transformed into a release version if the source is pre-release/SNAPSHOT? |  | false |

The 1st column indicates if it is recommended to include that property in your common-promotion.properties file or in a branch specific one (like dev-promotion.properties)

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| gitJenkinsSSHCredentials       |   | m | m |   |   |
| onlyOneReleaseBranch           |   | o | o |   |   |
| fromBranch                     |   | m | m |   |   |
| toBranch                       |   | m | m |   |   |
| increaseFromBranchMinorVersion |   | o | o |   |   |
| increaseToBranchPatchVersion   |   | o | o |   |   |
| fromSnaphotToReleaseOnToBranch |   | o | o |   |   |


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable