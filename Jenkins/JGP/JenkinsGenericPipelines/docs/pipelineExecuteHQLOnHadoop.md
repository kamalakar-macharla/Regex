## Introduction
This pipeline can be used to execute HQLs on Hadoop edge nodes.

A project will typically have one pipeline per environment where we want to execute the HQL(s):
- One for the development environment
- One for each release environment
- One for the UAT environment
- One for the production environment

## Assumptions
If the Jenkins job runs on a Windows box we have to use dos2unix to fix the CRLF on the files copied over to the Edge node which is Unix/Linux based.
For now, dos2unix is called on all the files in the project workspace with the exception of any .properties or .Jenkinsfile since they are used by Jenkins.
The dos2unix tool also automatically skips the conversion of binary files.

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-execute.Jenkinsfile
- common-execute.properties
- dev-execute.properties

### dev-execute.Jenkinsfile
You will leverage the pipelineExecuteHQLOnHadoop pipeline.

Here is an example of such a file:
```groovy
pipelineExecuteHQLOnHadoop(
  [
    propertiesFileName: 'dev-execute.properties',
    agent:'master',
    jenkinsJobTimeOutInMinutes: 15,
    cronExpression: 'H H 1 * *'
  ]
)
```

Note:  To set the cron so that it never executes the pipeline using cron, use:
```
cronExpression: ,
```

### common-execute.properties and dev-execute.properties
A typical project will have a different Jenkins job (Execute Pipeline) for each environement where we want to execute the HQLs.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-execute.properties that will contain all those properties that are the same for all your environments.

Then, for what is different for each environment, you can configure another properties file like "dev-execute.properties" or "prod-execute.properties".

#### Example properties files

Let's look at an example of jenkins/common-execute.properties file:  
```properties
run_HQL_command: ls -al
hql_files_folder: hql
context_commands: hostname && pwd &&
```

The jenkins/dev-execute.properties file for the same project could look like this:

```properties
emailJenkinsNotificationsTo=sheng_lin@manulife.com
edge_node: azcedledged005.mfcgd.com
user_name: edl_ca_prd
identity_file: /C/Users/AZCWVCBSCISP01Servic/.ssh/id_edge_prd
home_folder: /data-01/MFCGD.COM/edl_ca_prd/apps/cardb
run_HQL_command: chmod 744 /data-01/MFCGD.COM/edl_ca_prd/apps/cardb/IMIT_BigData_CARDB_Execute_HQL_PRD/run_cardb_stg_data_migration.sh && /data-01/MFCGD.COM/edl_ca_prd/apps/cardb/IMIT_BigData_CARDB_Execute_HQL_PRD/run_cardb_stg_data_migration.sh
```

You can actually specify more values than that in your properties file.  
Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

This pipelines also supports the following properties:

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No  | edge_node          | Hadoop edge node server name. |  |  | 
| No  | identity_file      | Path+Name of the identity file to be used for SSH on edge node. | | |
| No  | user_name          | User name for SSH on edge node. | | "." |
| No  | home_folder        | Home folder of the user that is used to SSH on edge node. | N/A |
| Yes | run_HQL_command    | Command(s) to run the HQL(s) on the edge node.  If many commands, provide them all in one string and add && between them.  |  |  |
| Yes | hql_files_folder   | Name of the folder that contains the HQL file(s) to be executed | | |
| Yes | context_commands   | Set of commands that help debug the SSH commands by providing some context. | | "hostname && pwd && " |
| No  | upstreamJobNames   | Path/Name of Jenkins job(s) that must have been executed successfuly before the execution of this pipelines.  An example would be [{Name: IMIT_Projects/IMIT_BigData/IMIT_Marketing_Automation/IMIT_BigData_MA_MMT_Execute_EVENT_HQL_PRD}, {Name: IMIT_Projects/IMIT_BigData/IMIT_Marketing_Automation/IMIT_BigData_MA_MMT_Execute_MAIN_HQL_PRD}]||null|

##### Properties Applicability

| Property Name | DotNetCore App | Java App | NodeJS App |
| ------------- | --- | ---------- | ---------- |
| edge_node          |   | m |   |
| identity_file      |   | m |   |
| user_name          |   | m |   |
| home_folder        |   | m |   |
| run_HQL_command    |   | m |   |
| hql_files_folder   |   | m |   |
| context_commands   |   | o |   |
| upstreamJobNames   |   | o |   |


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable

## Creating the Jenkins Execution job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-execute.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration.

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| feature     |  |   |  |  |  |
| development | Pipeline | "project_name"_Dev_Execute     | dev-execute.Jenkinsfile     | dev-execute.properties     | dev* |
| release     | Pipeline | "project_name"_Release_Execute | release-execute.Jenkinsfile | release-execute.properties | release* |
| production  | Pipeline | "project_name"_Prod_Execute    | prod-execute.Jenkinsfile    | prod-execute.properties    | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-execute.Jenkinsfile  
    dev-execute.properties  
    release-execute.Jenkinsfile  
    release-execute.properties  
    prod-execute.Jenkinsfile  
    prod-execute.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Then you can configure the remaining Jenkins jobs according to the table above.
