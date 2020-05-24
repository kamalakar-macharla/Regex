# Introduction

GitLab is the standard Source Code Management tool (code versioning) at Manulife.

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| Yes | gitLabAPITokenName | Id of the Credentials to the GitLab API | Your custom API Credentials Key | GitLabApiTokenText |
| Yes | gitJenkinsSSHCredentials | SSH Credentials to use git client on GitLab repo |  | null |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

| Property Name | AEM | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ---------- | ---------- | ------ | ----- |
| gitLabAPITokenName       | o | o | o | o | o |
| gitJenkinsSSHCredentials | o |   |   |   |   |


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
 * 
 
# How to create a GitLab Webhook 

1. Go to Jenkins and click on your job
2. On the left navigation click on Configure
3. Scroll down the page the build triggers. Notice the Build when a change is pushed to GitLab. The pipeline would have already check this for you but you just need to copy the URL beside it.
4. Go to GitLab to where your repo is located
5. On the left navigation go to Settings --> Intergrations
6. In the URL textbox paste the URL we just copied from Jenkins in step 3
7. Add the Secret Token in the other textbox. The secret token can be found in 2 places. You can either go to the *-ci.Jenkinsfile and look for the value of jenkinsJobSecretToken, Or from step 3 you can click on the advanced button in the same section to see the set Secret Token
8. Scroll down on the page and click "Add Webhook"