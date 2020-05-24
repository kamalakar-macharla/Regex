# Introduction

Git is the technology behind the standard Source Code Management tools (code versioning) at Manulife.

Currently only supporting GitFlow as defined here: https://confluence.manulife.io/pages/viewpage.action?pageId=49684369

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | gitFlowType | The Git flow used for this project | Currently only supporting GITFLOW | GITFLOW |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| gitFlowType       | o | o | o | o | o |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
 * 
 
