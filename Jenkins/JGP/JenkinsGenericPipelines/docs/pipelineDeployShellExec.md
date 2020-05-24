## Introduction
This pipeline pulls deployment files from artifactory, pass it to the shell script and executes shell script.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions :

1. No concourse pipelines as it does not deploy code into pcf

2. You are able to use the Mac slaves in Jenkins

3. Shell script is provided by the project team. It accepts below parameters:

DataDirectoryName - name of the folder in which data files exist

shellhosturl - host url which shell script needs to call 

shellhostport - port # which shell script needs ot use

USERNAME - credentials required by the shell script to execute any API 

PASSWORD - credentials required by the shell script to execute any API 

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- dev-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployShellExec pipeline as explained on [this page](docs/deploy.md).

### dev-deploy.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines (if required)

Then, for what is different for each pipeline, you can configure another properties file like "dev-deploy.properties".

#### Example properties files

The jenkins/common-deploy.properties file for the same project could look like this:

```properties
ShellScriptName: Name of the shell script to be executed

SourceDirectoryName: Name of the source directory, default is "src"

DataDirectoryName: Name of data directory where input files will be copied, default is "DataForShell"

```
The Jenkins/dev-deploy.properties file for the same project could look like this:

Settings:
shellscriptcredentialid: Jenkins credential vault's id

shellscriptcredentialidshellhosturl: Host url of API to be called by shell script

shellhostport: Port # of api to be called by shell script

Alongwith other common properties, below are additional properties configured in code

SourceDirectoryName - directory name where the input file will be stored in git repo, usually it is 'src' folder name

DataDirectoryName - directory name where the filtered files which need to be passed to the shell script will be stored on Jenkins server


You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

This pipelines also supports the following properties:

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No | smokeTestJenkinsJobName | Path and name of the Jenkins job that performs the smoke tests | | null |

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

In your project repository, you will have this structure:

```
jenkins/  
    dev-deploy.Jenkinsfile  
    dev-deploy.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-deploy.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
