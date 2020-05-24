## Introduction
A project will typically have a configuration for 4 Testing (TST) pipelines
- One for the feature & fix branches
- One for the development branch
- One for the release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This pipeline makes a few assumptions about your Postman project.

1. The API tests are stored in a git repository
2. The API tests, environment variables and collection files are created using Postman
3. Newman will be used to execute Postman collections
4. All the API test cases must be compiled into a Collection file (json file) and saved under root folder CollectionFiles (can have multiple collections) like SampleTest.json
5. All the environment variables must be compiled into a file (json file) and saved under root folder EnvironmentVariables (can have multiple environment variables) like SampleEnvironmentVariable.json

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-tst.Jenkinsfile
- common-tst.properties
- dev-tst.properties

### dev-tst.Jenkinsfile

You will leverage the pipelinePostmanTesting pipeline as explained on [this page](docs/tst.md).

Below is an example of a dev-test.Jenkinsfile:
```
 pipelinePostmanTesting([
    propertiesFileName:'dev-tst.properties',
    jenkinsJobInitialAgent: 'multi-platform-general',
    jenkinsJobTimeOutInMinutes: 10,
    collectionFile:'SampleTest.json',
    environmentVariable:'NoEnvironmentVariable\nSampleEnvironmentVariable.json',
    iterationCount:'1\n2\n3\n10'
])
```
If there is no environment variable, then dev-test.Jenkinsfile will be as follows:
```
 pipelinePostmanTesting([
    propertiesFileName:'dev-tst.properties',
    jenkinsJobInitialAgent: 'multi-platform-general',
    jenkinsJobTimeOutInMinutes: 10,
    collectionFile:'SampleTest.json',
    environmentVariable:'NoEnvironmentVariable',
    iterationCount:'1\n2\n3\n10'
])
```
If you are integrating test pipeline with Xray Jira project, then dev-test.Jenkinsfile will be as follows:
```
 pipelinePostmanTesting([
    propertiesFileName:'dev-tst.properties',
    jenkinsJobInitialAgent: 'multi-platform-general',
    jenkinsJobTimeOutInMinutes: 10,
    projectKey: 'yourXrayProjectKey',
    collectionFile:'SampleTest.json',
    environmentVariable:'NoEnvironmentVariable',
    iterationCount:'1\n2\n3\n10'
])
```
### common-tst.properties and dev-tst.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-tst.properties that will contain all those properties that are the same for all your testing pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-tst.properties".

If the test suite exists in a separate git repository than the main codebase then provide then testRepository and testBranch.

#### Example properties files

Let's look at an example of jenkins/common-tst.properties file:  
```properties
testRepository: ssh://git@example.com/repo.git
```

The jenkins/dev-tst.properties file for the same project could look like this:

```properties
testBranch: */dev
```

For the same project, the jenkins/release-tst.properties file would look like this instead:

```properties
testBranch: */release
```
In this example:
 * The development pipeline would use the dev branch on the test repository
 * The release pipeline would use the release branch on the test repository

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)
 * [GitLab (For source-code management)](docs/gitlab.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

| Common? | Mandatory | Property Name | Explanation  | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | testRepository | Git repository of the test suite | An ssh git url | null |
| No | No | testBranch | Branch to use in test suite repository | branch name | */master |
| No | Yes | jsonFilePath | Path to compiled JSON suite file | 'Test_Project/CollectionFiles/SampleTest.json' | null |
| No | No | jsonFilePath | Path to compiled JSON suite file | 'Test_Project/EnvironmentVariables/SampleEnvironmentVariable.json' | null |
| No | Yes | iterationCount | The number of iterations to run | positive integer | null |
## Creating the Jenkins Testing Job
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-tst.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

## Conclusion
That's it, you now have a Jenkins testing pipeline for your project.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| development | Pipeline | "project_name"_Dev_TST | dev-tst.Jenkinsfile | dev-tst.properties | dev* |
| release | Pipeline | "project_name"_Release_TST | release-tst.Jenkinsfile | release-tst.properties | release* |
| production | Pipeline | "project_name"_Prod_TST | prod-tst.Jenkinsfile | prod-tst.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-tst.Jenkinsfile  
    dev-tst.properties  
    feature-tst.Jenkinsfile  
    feature-tst.properties  
    release-tst.Jenkinsfile  
    release-tst.properties  
    prod-tst.Jenkinsfile  
    prod-tst.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-tst.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
