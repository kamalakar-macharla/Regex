## Introduction
This pipeline leverages MSBuild and publish profiles to deploy on Microsoft platforms like IIS, SharePoint and a file system.
A deployment pipeline will usually be triggered from a CI pipeline.

A project will typically have a pipeline per environment:
- One for the development environment
- One for each release environment
- One for the production environment

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- common-deploy.properties
- dev-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineMSBuildDotNetPublishProfile pipeline as explained on [this page](docs/deploy.md).

### common-deploy.properties and dev-deploy.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.
Since most of the configuration will be the same for all the environments, a common-deploy.properties file can be used to specify those values.

Let's look at an example of jenkins/common-deploy.properties file:  
```properties
projectName: MFTI.Service.TokenManager  
testProjectName: MFTI.Service.TokenManager.Test  

emailJenkinsNotificationsTo: john_doe@manulife.com  
```

The dev-deploy.properties file would look like this:
```properties
deployment_Credentials: IIS_Deploy_Dev  
publishProfiles: DevWebDeploy  
```

For the same project, the jenkins/release-deploy.properties file would look like this instead:
```properties
deployment_Credentials: IIS_Deploy_Release  
publishProfiles: ReleaseWebDeploy  
```

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

You can specify more values than that in your properties file.  
Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | projectRootFolder | Name of the folder that contains the solution file (.sln) if it is not in the root folder | | "." |
| Yes | Yes | projectName | DotNetCore project folder name |  | N/A |
| No  | No | testProjectName | DotNetCore test project folder name | Can be left empty of there is no test project | N/A |
| No  | Yes | deployment_Credentials | Name of the Jenkins credentials vault entry that contains the credentials to be used to deploy | | N/A |
| No  | Yes | publishProfiles | Name of the publish profiles to be used by MSBuild | | N/A | 

## Creating the Jenkins Deployment job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-deploy.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration.

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| feature |  |   |  |  |  |
| development | Pipeline | "project_name"_Dev_Deploy | dev-deploy.Jenkinsfile | dev-deploy.properties | dev* |
| release | Pipeline | "project_name"_Release_Deploy | release-deploy.Jenkinsfile | release-deploy.properties | release* |
| production | Pipeline | "project_name"_Prod_Deploy | prod-deploy.Jenkinsfile | prod-deploy.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/ 
    dev-deploy.Jenkinsfile  
    dev-deploy.properties  
    release-deploy.Jenkinsfile  
    release-deploy.properties  
    prod-deploy.Jenkinsfile  
    prod-deploy.properties  
src/
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-deploy.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
