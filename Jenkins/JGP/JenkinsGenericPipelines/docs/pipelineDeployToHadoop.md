## Introduction
This pipeline can be used to deploy applications on Hadoop edge nodes.

A project will typically have one pipeline per environment where we want to deploy:
- One for the development environment
- One for each release environment
- One for the UAT environment
- One for the production environment

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- common-deploy.properties
- dev-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployToHadoop pipeline as explained on [this page](docs/deploy.md).

### common-deploy.properties and dev-ci.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-deploy.properties".

#### Example properties files

Let's look at an example of jenkins/common-deploy.properties file:  
```properties
deploy_command: ls
downloadedFileName: package.jar
context_commands: hostname && pwd &&
snapshotRepo: example-maven-snapshot
releaseRepo: example-maven-release
```

The jenkins/dev-deploy.properties file for the same project could look like this:

```properties
edge_node: azcedledged005.mfcgd.com
user_name: edl_ca_dev
identity_file: /C/Users/AZCWVCBSCISP01Servic/.ssh/id_edge_dev
home_folder: /data-01/MFCGD.COM/edl_ca_dev
```

You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)  
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

This pipelines also supports the following properties:

edge_node", "Missing the edge node name.")
identity_file", "Missing the location (path + name) of the SSH identity file for the user to be used on the edge node.")
user_name", "Missing the name of the user to be used on the edge node.")
home_folder", "Missing the home folder of the user on the edge node.")
deploy_command", "Missing the command used to deploy the package on Hadoop.")
downloadedFileName", "Name the downloaded file will have on disk.")
context_commands", "Linux commands that provide some context about the location where the actions are taken.  Defaults to \"hostname && pwd &&\".  Could be assigned an empty string is not desired.", "hostname && pwd && ")

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No  | edge_node          | Hadoop edge node server name. |  |  | 
| No  | identity_file      | Path+Name of the identity file to be used for SSH on edge node. | | |
| No  | user_name          | User name for SSH on edge node. | | "." |
| No  | home_folder        | Home folder of the user that is used to SSH on edge node. | N/A |
| Yes | deploy_command     | Command(s) to deploy application on edge node.  If many commands, provide them all in one string and add && between them.  |  |  |
| Yes | downloadedFileName | Path & name for the file downloaded from Artifactory | | |
| Yes | context_commands   | Set of commands that help debug the SSH commands by providing some context. | | "hostname && pwd && " |

##### Properties Applicability

| Property Name | DotNetCore App | Java App | NodeJS App |
| ------------- | --- | ---------- | ---------- |
| edge_node          |   | m |   |
| identity_file      |   | m |   |
| user_name          |   | m |   |
| home_folder        |   | m |   |
| deploy_command     |   | m |   |
| downloadedFileName |   | m |   |
| context_commands   |   | o |   |


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
