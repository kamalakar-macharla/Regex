# Introduction
This *-ci.Jenkinsfile specifies which Generic Continuous Integration Pipeline should be used (e.g. pipelineDotNetCoreContinuousIntegration) and what is the corresponding Jenkins job configuration.

# Example
Let's look at an example of jenkins/dev-ci.Jenkinsfile:  

```groovy 
pipelineDotNetCoreContinuousIntegration(  
    [  
        propertiesFileName:'dev-ci.properties',  
        jenkinsJobInitialAgent: 'master',  
        jenkinsJobTimeOutInMinutes: 15,  
        jenkinsJobTriggerOnPush: true,  
        jenkinsJobTriggerOnMergeRequest: true,  
        jenkinsJobRegEx: '^dev$',              
        jenkinsJobSecretToken: 'MyJobSecretToken',  
    ]
)
``` 

# Continuous Integration Pipeline Types

As you can see, the previous example declares a pipelineDotNetCoreContinuousIntegration pipeline.  
The following Continuous Integration pipeline types are currently supported:

| Name | Description |
| ------------- | ------------ |
| pipelineAEMMavenContinuousIntegration | Continuous Integration Pipeline for Adobe Experience Manager projects (with Maven) |
| pipelineDotNetClassicContinuousIntegration | Continuous Integration Pipeline for DotNet Classic (Non Core) projects | 
| pipelineDotNetCoreContinuousIntegration | Continuous Integration Pipeline for DotNet Core projects | 
| pipelineGoContinuousIntegration | Continuous Integration Pipeline for GoLang projects | 
| pipelineGradleContinuousIntegration | Continuous Integration Pipeline for Gradle projects | 
| pipelineJavaMavenContinuousIntegration | Continuous Integration Pipeline for Java Maven projects | 
| pipelineNodeContinuousIntegration | Continuous Integration Pipeline for NodeJS projects |
| pipelineNifiContinuousIntegration | Continuous Integration Pipeline for Nifi projects |
| pipelinePythonContinuousIntegration | Continuous Integration Pipeline for Python projects |
| pipelineSwiftContinuousIntegration | Continuous Integration Pipeline for Swift projects |
| pipelineShellExecContinuousIntegration | Continuous Integration Pipeline for shell script execution projects |

# Configuration 

All pipeline types support the following configuration options:

| Property Name | Explaination | Possible Values |
| ------------- | ------------ | --------------- |
| propertiesFileName | Name of the properties file that will also be used by this job.  That properties file will also have to be located under the jenkins/ folder | A file name in this format: environmentname-ci.Jenkinsfile |
| jenkinsJobInitialAgent | Label of the Jenkins agent where the job should run. | master or the label of a specific kind of Jenkins slave node. It is recommended that you review this [Jenkins Agent Document](http://cpcnissgwp01.americas.manulife.net:23860/display/DG/Jenkins+Nodes) to provide a suggested label to use for your pipeline. You can also combine multiple labels together for a more speicifc requirement. In most cases it is not best practice to target one specific build node |
| jenkinsJobTimeOutInMinutes | Specifies how much time your job should run before it is aborted.  Allows Jenkins to stop jobs that are hanged for some reasons. | Integer value (in minutes) |
| jenkinsJobTriggerOnPush | Indicates if the job should be triggered when someone pushes code to the branch this job is attached to | true or false |
| jenkinsJobTriggerOnMergeRequest | Indicates if the job should be triggered when someone opens a merge request to the branch this job is attached to | true or false |
| jenkinsJobRegEx | Regular expression that specifies which branches that Jenkins job is for. | A regular expression on branch names |
| jenkinsJobSecretToken | Token that GitLab will use to trigger your Jenkins job. | This should be the project name for simplicity |