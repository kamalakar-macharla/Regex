# Introduction

Fortify is the Manulife standard tool to check Manulife source code for security vulnerabilities.

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | fortifyScanTree | If set to a directory such as . with a possible prefix -e EXCLUDE\_MASK, this scans the source code tree ignoring calls to libraries.  The exclude mask should be wrapped with double quotes and contain space-separated Ant masks.  The wrapper script will process /DIRMASK/ and /FILEMASK to skip directories or files regardless of their position in the tree.  Beware of a possible match against a parent directory.  | -e "/tests/ /test\*.js" . | null |
| Yes | fortifyTriggers | The regular expression for branch names that need Fortify scans.  When a Gitlab merge request is approved and the repository has a setting to avoid empty merge commits by fast-forwarding, this value will check the target branch name of the request. | master&#124;prod | null (! dev&#124;devel&#124;develop&#124;dev/.\*&#124;feature/.\*&#124;fix/.\*&#124;hotfix/.\*&#124;patch-.\*) |
| Yes | fortifyGating | Whether to break builds on detecting high risk OWASP issues |  | true |
| Yes | fortifyApp | The name of the application in the Software Security Center dashboard | | null |
| Yes | fortifyVer | The version (branch) of the application in the Software Security Center dashboard | | null |
| Yes | fortifyAppDescr | The description of the application in the Software Security Center dashboard |  | null |
| Yes | fortifyTokenName | The name of the Jenkins credential containing the SSC token with application management permission |  | FORTIFY\_MANAGE\_APPLICATION\_TOKEN |
| Yes | fortifyServer | The URL of the SSC server |  | https://fortify.americas.manulife.net/ssc |
| Yes | fortifyScriptWeb | The project ID, branch and the directory containing Fortify wrapper scripts |  | 888,master,sample-builds/ |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

# Properties Applicability

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| fortifyScanTree | o | o | o | o | o |
| fortifyTriggers | o | o | o | o | o |
| fortifyGating   | o | o | o | o | o |
| fortifyApp      | o | o | o | o | o |
| fortifyVer      | o | o | o | o | o |
| fortifyAppDescr | o | o | o | o | o |
| fortifyTokenName| o | o | o | o | o |
| fortifyServer   | o | o | o | o | o |
| fortifyScriptWeb| o | o | o | o | o |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
