## Introduction
This pipeline can be used to update a database structure and data as part of a Continuous Database Delivery process.

## Assumptions
In order to use this migration pipeline you will need to also have SQL migration scripts.
Typically, those scripts will be stored in a subfolder of the project db/migration folder (see the end of this page for folder structure) and follow the proper [naming convention](https://flywaydb.org/documentation/migrations#naming).

A project will typically have one pipeline per environment where we want to deploy:
- One for the development environment
- One for the test/qa environment
- One for the UAT environment
- One for the production environment

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-migration.Jenkinsfile
- common-migration.properties
- dev-migration.properties

### dev-migration.Jenkinsfile
You will leverage the pipelineFlywayDatabaseMigration pipeline as explained on [this page](docs/migration.md).

### common-migration.properties and dev-migration.properties
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-migration.properties that will contain all those properties that are the same for all your deployment pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-migration.properties".

#### Example properties files

Let's look at an example of jenkins/common-migration.properties file:  
```properties
emailJenkinsNotificationsTo: francois_ouellet@manulife.com
```

The jenkins/dev-migration.properties file for the same project could look like this:

```properties
url: jdbc:mysql://localhost:3306/devday?useUnicode=true&characterEncoding=utf8&useSSL=false
dbCredentials: JenkinsCredID
locations: filesystem:./db/migration/common,./db/migration/dev
```

Pay special attention to the locations property value.  Since this is the configuration for dev, it includes the common scripts + the scripts for dev.

You can actually specify more values than that in your properties file.  Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | ------------ | --------------- | ------------- |------------- |
| No | Yes | url                      | URL to be used to connect to the database | jdbc:mysql://localhost:3306/devday?useUnicode=true&characterEncoding=utf8&useSSL=false |  | 
| No | Yes | dbCredentials                 | Jenkins credential ID that maps to a user / pass type to connect to the db |  |  | 
| No | No  | locations                | Path to the flyway migration scripts to be applied on the database | | filesystem:./db/migration/common |
| No | No  | extraFlywayParams        | Can be used to pass additional parameter to the flyway call | | null |
| No | No  | deploymentJenkinsJobName | Can be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this migration pipeline | | null |

## Creating the Jenkins Migration job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-migration.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration.

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| feature |  |   |  |  |  |
| development | Pipeline | "project_name"_Dev_Migration | dev-migration.Jenkinsfile | dev-migration.properties | dev* |
| qa | Pipeline | "project_name"_QA_Migration  | qa-migration.Jenkinsfile | qa-migration.properties | release* |
| uat | Pipeline | "project_name"_UAT_Migration  | uat-migration.Jenkinsfile | uat-migration.properties | release* |
| production | Pipeline | "project_name"_Prod_Migration  | prod-migration.Jenkinsfile | prod-migration.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    common-migration.properties
    dev-migration.Jenkinsfile  
    dev-migration.properties  
    qa-migration.Jenkinsfile  
    qa-migration.properties  
    uat-migration.Jenkinsfile  
    uat-migration.properties  
    prod-migration.Jenkinsfile  
    prod-migration.properties  
src/  
db/
  migration/
    common/
      V1__XYZ.sql
      V2__ABC.sql
    dev/
      V2.1__SomethingThatsDoneOnlyInDev.sql
    qa/
    uat/
    prod/
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-migration.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
