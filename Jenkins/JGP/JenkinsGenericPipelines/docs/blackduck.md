# Introduction

BlackDuck is used by Manulife to make sure projects are compliant with our Open-Source Governance around:
 * Security vulnerabilities
 * Licensing
 * Operational risk

# Supported Properties

| Common? | Property Name | Explaination | Possible Values | Default Value |
| -------------| ------------- | ------------ | --------------- | ------------- |
| Yes | hubTriggers | The regular expression for branch names that need Blackduck scans. | develop&#124;master | null (! dev&#124;devel&#124;develop&#124;dev/.\*&#124;feature/.\*&#124;fix/.\*&#124;hotfix/.\*&#124;patch-.\*) |
| Yes | hubVersionDist | Specifies how this app is distributed.   Look at <b> <a href="https://stack.manulife.io/questions/473/which-option-should-i-choose-when-configuring-the-distribution-of-a-project-in" target="_blank">this post</a> </b> for details. | One of : INTERNAL, PaaS, EXTERNAL | |
| Yes | hubExclusionPattern | Can be used to exclude things from the BlackDuck scanning | Path(s) to exclude  | /Nothing/To/Exclude/ |
| Yes | hubExcludedModules | Can be used to exclude modules from the BlackDuck scanning | Path(s) to exclude  | Nothing\_To\_Exclude/ |
| No  | hubFailOnSeverities | BlackDuck issues that should fail the pipeline | Comma separated list of the followings: CRITICAL,BLOCKER,MAJOR,MINOR,TRIVIAL | CRITICAL,BLOCKER,MAJOR,MINOR,TRIVIAL |
| No  | hubVersionPhase | Tells BlackDuck if our project is in development or if this is a release version | DEVELOPMENT / RELEASED | |
| No  | hubFailPipelineOnFailedOpenSourceGovernance | Indicates if failing the open-source governance gate should have this pipeline fail.  Should be set to false in DEV and true for other branches | true or false | true |
| Yes | hubTimeoutMinutes | Timeout for Black Duck step | 10 | 9999 |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| hubTriggers                                  | o | o | o | o | o |
| hubVersionDist                               | m | m | m | m | m |
| hubExclusionPattern                          | o | o | o | o | o |
| hubExcludedModules                           | o | o | o | o | o |
| hubFailOnSeverities                          | o | o | o | o | o |
| hubVersionPhase                              | m | m | m | m | m |
| hubFailPipelineOnFailedOpenSourceGovernance  | o | o | o | o | o |
| hubTimeoutMinutes                            | o | o | o | o | o |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
