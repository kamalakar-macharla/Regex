## Introduction
This pipeline can be used to deploy applications on AEM Servers.

A project will typically have one pipeline per environment where we want to deploy:
- One for each qa environment
- One for the stage environment

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- qa-cd.jenkinsfile
- common-deploy.properties
- qa-cd.properties

### qa-cd.Jenkinsfile
You will leverage the pipelineDeployToAEM pipeline as explained on [this page](docs/deploy.md).

### common-deploy.properties and dev-ci.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "qa-cd.properties".

#### Example properties files

Let's look at an example of jenkins/common-deploy.properties file:  
```properties
# NOTIFICATION - SLACK
slackChannel:aem-devops-releases
slackTokenCredentialID:SLACK-TOKEN-ENT-CET

# Artifactory
artifactoryInstance:Artifactory-Global-Prod
artifactoryDeploymentPattern:*.zip
releaseRepo:mfc-dig-maven-release
snapshotRepo:mfc-dig-maven-snapshot
artifactoryCredentials:Artifactory-Generic-Account
artifactoryApiToken:artifactoryAPIToken

# Git
#gitLabAPITokenName:CEA-GITLAB-API
gitLabSSHCredentialsId:dsdevops-ssh

#groupID sub-folder where the artifact is stored.
groupId:/ca/manulife/dxp/

#Dispatcher Clear Cache
clearCacheJenkinsJobName:Dig_DigitalSolutions/Dig_AEM/Dig_Global/Dig_AEM_Dispatcher_DEV_Clear_Cache
```

The jenkins/qa-cd.properties file for the same project could look like this:

```properties
#This file will contain environment and credentials.
downloadPattern: aem-global

#Deploy Commands
coreArtifactDeployCommand:curl -k -X POST -v -u admin:${AEM_ADMIN_CREDENTIALS_PSW}  -F name=${apps_File_Name} -F file=@"${apps_File_Path}" -F strict=true  -F install=true  http://${AEM_AUTHOR_CREDENTIALS_USR}:4502/crx/packmgr/service.jsp --progress-bar
uiArtifactDeployCommand:  curl -k -X POST -v -u admin:${AEM_ADMIN_CREDENTIALS_PSW}  -F name=${apps_File_Name} -F file=@"${apps_File_Path}" -F strict=true  -F install=true  http://${AEM_PUBLISHER_CREDENTIALS_USR}:4503/crx/packmgr/service.jsp --progress-bar

#Credentials
AEMAdminCredentials: AEM-QA-ADMIN-6.5
AEMAuthorCredentials: AEM-QA-Author-6.3
AEMPublisherCredentials: AEM-QA-Publisher-6.3

#FE Components -Below properties is only for Retirements Redefined Project.
#appFEComponentGitBranch: develop

#This property is used to hold the package name.
gitlabSourceRepoName:aem-global.ui.apps

projectRootFolder: ui.apps
AEMProjectMode:global

#AEMExecuteMode possible values: dev, qa and stage
AEMExecuteMode:qa
#AEMEnvironment holds ENV1 which is dev1,qa1. ENV2 which is dev2,qa2.
AEMEnvironment:ENV1
```

You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)  
 * [Notifications (For notifications on email, Slack or Mattermost)](docs/notifications.md)  

| Common? | Mandatory| Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | ------------ | --------------- | ------------- |------------- |
| No | Yes | AEMAdminCredentials | AEM Credentials and host ips that are injected into the build commands  | AEM-QA-ADMIN ||
| No | Yes | AEMAuthorCredentials | AEM Credentials and host ips that are injected into the build commands | AEM-QA-Author-6.3 ||
| No | Yes | AEMPublisherCredentials | AEM Credentials and host ips that are injected into the build commands | AEM-QA-Publisher-6.3 ||
| No | No | AEMPublisher2Credentials | AEM Credentials and host ips that are injected into the build commands | | null|
| No | No | gitlabSourceRepoName | This property is used to hold the package name. Like for global we use aem-global.ui. | | null|
| No | No | AEMExecuteMode | This property is used to hold the mode of execution. | dev,qa and stage | null|
| No | No | AEMEnvironment | This property is used to hold the environment like dev1 or dev2. Use ENV1 while executing on dev1, qa1. Use ENV2 while executing on dev2, qa2.| ENV1 or ENV2 | null|

## Creating the Jenkins Deployment job for the qa branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-cd.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: If you encounter any issues while configuring the pipeline, please contact ci-cd-team.

## Conclusion
That's it, you now have a Jenkins pipeline for your qa branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| feature |  |   |  |  |  |
| release | Pipeline | "project_name"_Release_Deploy | qa-cd.Jenkinsfile | qa-cd.properties | release* |
| production | Pipeline | "project_name"_Prod_Deploy | prod-cd.Jenkinsfile | prod-cd.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    qa-cd.Jenkinsfile  
    qa-cd.properties  
    prod-cd.Jenkinsfile  
    prod-cd.properties  
    common-deploy.properties
src/  
```

You can follow the same process, as for qa, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-deploy.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
