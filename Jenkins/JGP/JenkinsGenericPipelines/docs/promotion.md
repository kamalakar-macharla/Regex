# Introduction
This *-promotion.Jenkinsfile specifies which Generic Promotion Pipeline should be used (e.g. pipelinePromotionMaven) and what is the corresponding Jenkins job configuration.

# Example
Let's look at an example of jenkins/dev-promotion.Jenkinsfile:  

```groovy 
pipelinePromotionDotNetCore(  
    [    
        propertiesFileName:'dev-promotion.properties',    
        jenkinsJobInitialAgent: 'master',    
        jenkinsJobTimeOutInMinutes: 15,  
    ]
)
``` 

# Continuous Integration Pipeline Types

As you can see, the previous example declares a pipelinePromotionDotNetCore pipeline.  
The following Continuous Integration pipeline types are currently supported:

| Name | Description |
| ------------- | ------------ |
| pipelinePromotionDotNetCore | Promotion Pipeline for DotNet Core projects | 
| pipelinePromotionGradle | Promotion Pipeline for Gradle projects | 
| pipelinePromotionMaven | Promotion Pipeline for Maven projects | 
| pipelinePromotionNodeJS | Promotion Pipeline for NodeJS projects | 

# Configuration 

All pipeline types support the following configuration options:

| Property Name | Explaination | Possible Values |
| ------------- | ------------ | --------------- |
| propertiesFileName | Name of the properties file that will also be used by this job.  That properties file will also have to be located under the jenkins/ folder | A file name in this format: environmentname-promotion.Jenkinsfile |
| jenkinsJobInitialAgent | Label of the Jenkins agent where the job should run. | master or the label of a specific kind of Jenkins slave node. It is recommended that you review this [Jenkins Agent Document](http://cpcnissgwp01.americas.manulife.net:23860/display/DG/Jenkins+Nodes) to provide a suggested label to use for your pipeline. You can also combine multiple labels together for a more speicifc requirement. In most cases it is not best practice to target one specific build node |
| jenkinsJobTimeOutInMinutes | Specifies how much time your job should run before it is aborted.  Allows Jenkins to stop jobs that are hanged for some reasons. | Integer value (in minutes) |