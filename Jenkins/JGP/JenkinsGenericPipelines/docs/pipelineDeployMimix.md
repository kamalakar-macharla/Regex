## Introduction
This pipeline is responsible to deploy Nifi code artifacts to Nifi servers. It is used to deploy code from Dev to Test, Test to UAT and UAT to Prod.
## Assumptions
This Jenkins Generic Pipeline makes the following assumptions:

1. Credentials are provided in the Jenkins credentials vault (to be enhanced for secrets repo and hashicorp tool in future) to connect to Nifi servers.
2. Environment yaml file is provided by the project to connect to various environments.

## CIFS Plugin

Here we are using CIFS Plugin to deploy the source code from one machine to Window shared machine. It has been setup and configured with all the required values except *TargetLocation*
For more about CIFS please read [this page](https://wiki.jenkins.io/display/JENKINS/Publish+Over+CIFS+Plugin )

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 2 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- dev-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployToNifi pipeline as explained on [this page](docs/deploy.md).

### dev-deploy.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines (if required)

Then, for what is different for each pipeline, you can configure another properties file like "dev-deploy.properties".

#### Example properties files

The jenkins/dev-deploy.properties file for the same project could look like this:

```properties

```


You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email, Slack or Mattermost)](docs/notifications.md)

This pipelines also supports the following properties:

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No | smokeTestJenkinsJobName | Path and name of the Jenkins job that performs the smoke tests | | null |
| No | TargetLocation | Location where you want to deploy your files |//entsserver24.americas.manulife.net/dfs/shared/can/CDDO | null |
| No | removePrefix | remove the Unnecessary folder/path that is as a prefix, that depends on the soruce file Structure  | src/download_artifacts| null |
| No | sourceFiles | The files that going to be copy to the destination location | .RSL files| null |


##### Properties Applicability

| Property Name | DotNetCore App | Java App | NodeJS App |
| ------------- | --- | ---------- | ---------- |
| smokeTestJenkinsJobName | o  | o | o |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable

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


## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| development | Pipeline | "project_name"_Dev_Deploy | dev-deploy.Jenkinsfile | dev-deploy.properties | dev* |
| release | Pipeline | "project_name"_Release_Deploy | release-deploy.Jenkinsfile | release-deploy.properties | release* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-deploy.Jenkinsfile  
    dev-deploy.properties  
    test-deploy.Jenkinsfile  
    test-deploy.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-deploy.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
