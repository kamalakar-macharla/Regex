## Introduction
This project will be used to archive mongodb database/collections and store on git repository and also to restore the files from git to mongodb database.
- One for the development branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-mongodb.Jenkinsfile
- common-mongodb.properties
- dev-mongodb.properties

### dev-mongodb.Jenkinsfile

You will leverage the pipelineMongodbArchiveRestore pipeline.

### common-mongodb.properties and dev-mongodb.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-mongodb.properties that will contain all those properties that are the same for all your backup pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-mongodb.properties".

#### Example properties files

Let's look at an example of jenkins/common-mongodb.properties file:  

```properties
#GITLAB
enableGitLabNotification: true

#SLACK -Please use Slack-Token-CEA-ALL
#slackTokenCredentialID: EXAMPLE-TOKEN-SLACK
#slackChannel: ci-cd-team

#SSH 
gitLabSSHCredentialsId: dsdevops-ssh
```
The jenkins/dev-mongodb.properties file for the same project could look like this:

```properties
mongodb_credentials:Onboarding_MongoDB
mongodb_database:default

#If collections is null, complete db collections will be downloaded and checked into git repo.
mongodb_collections:buconfigs,gitlabprojects
```
Note that in the above properties file, mongodb_collections is optional. If we don't specify the property then whole db will be archived up.
Same apply for restore, if we don't specify the mongodb_collections property then whole db will be restored instead of individual collections.

Please note that the mongodb_credentials will hold the complete mongodb uri in the password field.

## Creating the Jenkins mongodb job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the sCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-mongodb.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

In your project repository, you will have this structure:

```
jenkins/  
    common-mongodb.properties
    dev-mongodb.Jenkinsfile
    dev-mongodb.properties
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-mongodb.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.