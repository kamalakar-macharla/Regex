# Introduction
This *-migration.Jenkinsfile specifies which Generic Migration Pipeline should be used (e.g. pipelineFlywayDatabaseMigration) and what is the corresponding Jenkins job configuration.

# Example
Let's look at an example of jenkins/dev-migration.Jenkinsfile:  

```groovy
pipelineFlywayDatabaseMigration(  
    [  
    propertiesFileName:'dev-migration.properties',  
    jenkinsJobInitialAgent: 'master',  
    jenkinsJobTimeOutInMinutes: 15,  
    ]
)
``` 

# Deployment Pipeline Types

As you can see, the previous example declares a pipelineFlywayDatabaseMigration pipeline.  
The following Continuous Integration pipeline types are currently supported:

| Name | Description |
| ------------- | ------------ |
| pipelineFlywayDatabaseMigration | Migration pipeline for databases using the Flyway tool. | 

# Configuration 

All pipeline types support the following configuration options:

| Property Name | Explaination | Possible Values |
| ------------- | ------------ | --------------- |
| propertiesFileName | Name of the properties file that will also be used by this job.  That properties file will also have to be located under the jenkins/ folder | A file name in this format: environmentname-migration.Jenkinsfile |
| jenkinsJobInitialAgent | Label of the Jenkins agent where the job should run. | master or the label of a specific kind of Jenkins slave node. It is recommended that you review this [Jenkins Agent Document](http://cpcnissgwp01.americas.manulife.net:23860/display/DG/Jenkins+Nodes) to provide a suggested label to use for your pipeline. You can also combine multiple labels together for a more speicifc requirement. In most cases it is not best practice to target one specific build node |
| jenkinsJobTimeOutInMinutes | Specifies how much time your job should run before it is aborted.  Allows Jenkins to stop jobs that are hanged for some reasons. | Integer value (in minutes) |