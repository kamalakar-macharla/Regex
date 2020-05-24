## Introduction
This pipeline can be used to deploy reports on Microsoft SSRS servers.

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
You will leverage the pipelineDeployReportToSSRS pipeline as explained on [this page](docs/deploy.md).

### common-deploy.properties and dev-ci.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-deploy.properties that will contain all those properties that are the same for all your deployment pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-deploy.properties".

#### Example properties files

Let's look at an example of jenkins/common-deploy.properties file:  
```properties
reportNames: Paramed
sourceReportsFolder: src
targetSSRSFolder: /DemoReport
```

The jenkins/dev-deploy.properties file for the same project could look like this:

```properties
SSRSServerURL: http://mlipsgbvosql/reportserver
```

You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

This pipelines also supports the following properties:

propertiesCatalog.addMandatoryProperty('SSRSServerURL', '')
    propertiesCatalog.addMandatoryProperty('targetSSRSFolder', '')
    propertiesCatalog.addMandatoryProperty('sourceReportsFolder', '')
    propertiesCatalog.addMandatoryProperty('reportNames', '')
    propertiesCatalog.addOptionalProperty('deployScriptName', '', '')

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No  | SSRSServerURL       | URL of SSRS server such as http://mlipsgbvosql/reportserver |  |  |
| Yes | targetSSRSFolder    | Target folder for report in SSRS server. |  |  | 
| Yes | sourceReportsFolder | Name of the folder that contains the reports to be deployed. | | |
| Yes | reportNames         | Comma separated list of report names.  Do not include ".rdl" in the name(s). | | |
| Yes | deployScriptName    | Name of the deployment script defined in Jenkins.  Defaults to "Deploy_Report.rss". |  | Deploy_Report.rss |

##### Properties Applicability

| Property Name       | Applicability |
| ------------------- | ----- |
| SSRSServerURL       | m |
| targetSSRSFolder    | m |
| sourceReportsFolder | m |
| reportNames         | m |
| deployScriptName    | o |

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
    common-deploy.properties
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
