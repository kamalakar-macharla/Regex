# Introduction

GitLab Build Status Notifier will update GitLab based on the Jenkins build status. The following are the Jenkins status mappings to GitLab statuses:

| Jenkins        | GitLab   |
|----------------|----------|
| Build running  | running  |
| Build success  | success  |
| Build failed   | failed   |
| Build unstable | failed   |
| Build aborted  | canceled |

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | gitLabEnableNotifications | Should the integration be enabled? | "true" or "false" | true |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| gitLabEnableNotifications       | o | o  | o | o | o |


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
