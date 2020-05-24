## Introduction
This pipeline can be used to deploy applications to PCF using the fly command line tool. The concourse Enterprise pipelines will need to be leveraged in order for this to work as expected. For more information visit [Yammer](https://www.yammer.com/manulife.com/#/threads/show?threadId=1097614990) 

A project will typically have one pipeline per environment where we want to deploy:
- One for the development environment
- One for each release environment

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your Concourse Fly Deployment:

1. You have already coded your concourse pipelines and added it to your source code
3. You are able to use the Mac slaves in Jenkins

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- common-deploy.properties
- dev-deploy.Jenkinsfile
- dev-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployToPCF pipeline as explained on [this page](docs/deploy.md).

### common-deploy.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines (if required)

Then, for what is different for each pipeline, you can configure another properties file like "dev-deploy.properties".

#### Example properties files

Let's look at an example of jenkins/common-deploy.properties file:  
```properties
enterprisePCFCredentials: ExampleConcourseFly-Dev
enterprisePCFSpace: CDN-EXAMPLES
concoursePipelineName: cdt-examples-dev-ci
concourseScriptPath: ./updatePipeline.sh
```

The jenkins/dev-deploy.properties file for the same project could look like this:

```properties
concourseJobName: deploy-dev-node-example
```

You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

This pipelines also supports the following properties:

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| Yes | enterprisePCFCredentials | Jenkins credential ID name which contains the username and password to authenicate to concourse |  | N/A |
| Yes | enterprisePCFSpace | Concourse Team the concourse team name assigned without environment trailing |  | N/A | 
| Yes | concoursePipelineName | The concourse pipeline name of your project | | N/A |
| No | concourseJobName | The concourse job name that you wish to trigger | | N/A |
| Yes | concourseURL | URL to the concourse |  | https://concourse.platform.manulife.io |
| Yes | concourseScriptPath | Source code location where the pipeline code can be found and deployed. Assumes pipeline.yml is used for the pipeline and config.yml for credential storage  | Example: concourse_pipeline | concourse_pipeline |
| No | smokeTestJenkinsJobName | Path and name of the Jenkins job that performs the smoke tests | | null |

##### Properties Applicability

| Property Name | DotNetCore App | Java App | NodeJS App |
| ------------- | --- | ---------- | ---------- |
| enterprisePCFCredentials| m | m | m |
| enterprisePCFSpace| m  | m | m |
| concoursePipelineName   | m  | m | m |
| concourseJobName        | m  | m | m |
| concourseURL            | o  | o | o |
| concourseScriptPath     | o  | o | o |
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
| release | Pipeline | "project_name"_Release_Deploy | release-pcf-deploy.Jenkinsfile | release-deploy.properties | release* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-deploy.Jenkinsfile  
    dev-deploy.properties  
    release-pcf-deploy.Jenkinsfile  
    release-pcf-deploy.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-pcf-deploy.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
