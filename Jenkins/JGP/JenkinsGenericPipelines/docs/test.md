# Introduction
This *-test.Jenkinsfile specifies which Generic Integration Test Pipeline should be used (e.g. pipelineJavaMavenIntegrationTest) and what is the corresponding Jenkins job configuration.

# Example
Let's look at an example of jenkins/dev-test.Jenkinsfile:  

```groovy 
pipelineJavaMavenContinuousIntegration(  
    [  
        propertiesFileName:'dev-test.properties',  
        jenkinsJobInitialAgent: 'master',  
        jenkinsJobTimeOutInMinutes: 15  
    ]
)
``` 

# Continuous Integration Pipeline Types

As you can see, the previous example declares a pipelineJavaMavenIntegrationTest pipeline.  
The following Continuous Integration pipeline types are currently supported:

| Name | Description |
| ------------- | ------------ |
| pipelineJavaMavenContinuousIntegration | Integration Test Pipeline for Java Maven projects | 

* Support for other Intergration Test pipelines will be added in the future. 

# Configuration 

All pipeline types support the following configuration options:

| Property Name | Explaination | Possible Values |
| ------------- | ------------ | --------------- |
| propertiesFileName | Name of the properties file that will also be used by this job.  That properties file will also have to be located under the jenkins/ folder | A file name in this format: environmentname-test.Jenkinsfile |
| jenkinsJobInitialAgent | Label of the Jenkins agent where the job should run. | master or the label of a specific kind of Jenkins slave node |
| jenkinsJobTimeOutInMinutes | Specifies how much time your job should run before it is aborted.  Allows Jenkins to stop jobs that are hanged for some reasons. | Integer value (in minutes) |
