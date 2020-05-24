# Introduction
This *-deploy.Jenkinsfile specifies which Generic Deployment Pipeline should be used (e.g. pipelineMSBuildDotNetPublishProfile) and what is the corresponding Jenkins job configuration.

# Example
Let's look at an example of jenkins/dev-deploy.Jenkinsfile:  

```groovy
pipelineMSBuildDotNetPublishProfile(  
    [  
    propertiesFileName:'dev-deploy.properties',  
    jenkinsJobInitialAgent: 'multi-platform-general',  
    jenkinsJobTimeOutInMinutes: 15,  
    ]
)
``` 

# Deployment Pipeline Types

As you can see, the previous example declares a pipelineDotNetCoreContinuousIntegration pipeline.  
The following Continuous Integration pipeline types are currently supported:

| Name | Description |
| ------------- | ------------ |
| pipelineDeployReportToSSRS | Deploy report on Microsoft SSRS server | 
| pipelineDeployToProvisioningAPI | Deployment Pipeline to PCF for all environments without the use of concourse | 
| pipelineDeployToHadoop | Deploy application on Hadoop edge node |
| pipelineDeployToPCF | Deployment Pipeline on PCF using the "fly" command line tool | 
| pipelineMSBuildDotNetPublishProfile | Deployment Pipeline on PCF using the "cf" command line tool |
| pipelineDeployShellExec | Deploy files to the shell script which executes shell commands |
| pipelineDeployToNifi | Deploy Nifi code |
| pipelineDeployToAEM  | Deploy AEM code  |

# Configuration 

All pipeline types support the following configuration options:

| Property Name | Explaination | Possible Values |
| ------------- | ------------ | --------------- |
| propertiesFileName | Name of the properties file that will also be used by this job.  That properties file will also have to be located under the jenkins/ folder | A file name in this format: environmentname-ci.Jenkinsfile |
| jenkinsJobInitialAgent | Label of the Jenkins agent where the job should run. | master or the label of a specific kind of Jenkins slave node. It is recommended that you review this [Jenkins Agent Document](http://cpcnissgwp01.americas.manulife.net:23860/display/DG/Jenkins+Nodes) to provide a suggested label to use for your pipeline. You can also combine multiple labels together for a more speicifc requirement. In most cases it is not best practice to target one specific build node |
| jenkinsJobTimeOutInMinutes | Specifies how much time your job should run before it is aborted.  Allows Jenkins to stop jobs that are hanged for some reasons. | Integer value (in minutes) |