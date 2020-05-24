## Introduction
A project will typically have a configuration for 3 Integration Test pipelines
- One for the development branch
- One for the release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-test.Jenkinsfile
- common-test.properties
- dev-test.properties

### dev-test.Jenkinsfile

You will leverage the pipelineNodeIntegrationTest pipeline as explained on [this page](docs/test.md).

### common-test.properties and dev-test.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-test.properties that will contain all those properties that are the same for all your Integration Test pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-test.properties".

#### Example properties files

Let's look at an example of jenkins/common-test.properties file:  
```properties
emailJenkinsNotificationsTo: sqa@manulife.com  
```

The jenkins/dev-test.properties file for the same project could look like this:

```properties:
nodeIntegrationTestCommand: npm run test:devInt
promotionJenkinsJobName: ExamplePipelinePromoteToRelease
```

For the same project, the jenkins/release-test.properties file would look like this instead:

```properties:
nodeIntegrationTestCommand: npm run test:releaseInt
promotionJenkinsJobName: ExamplePipelinePromoteToMaster
```
In this example, if test fails:
 * It will be considered as "failed" and won't be promoted to the release or production branch.

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [GitLab (For source-code management)](docs/gitlab.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| No | No | promotionJenkinsJobName | The code promotion job | Your promotion job name | N/A |
| Yes | No | nodeIntegrationReportName | Report name on jenkins | Your report name | Integration report |
| Yes | No | nodeIntegrationReportHtmlFile | Your test report html file | index.html | N/A |
| Yes | No | nodeIntegrationReportRelativePath | Your test report location | serenatiy/report | N/A |
| No | Yes | nodeIntegrationTestCommand | Node test command | Your special Node test command| N/A |
| No | Yes | nodeBuildCommand | Node build command | Your special Node build command | N/A |
| Yes | No | nodeTestReportGenerateCommand | Node report generation command | Your special Node report generation command | N/A |

## Creating the Jenkins Integration Test job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-test.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

## Supported Test Report
When the test is completed, any html report can be published to Jenkins using the publish plugin

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ------------- | ------------- | ------------- | ------------ | --------------- | --------------- |
| development | Pipeline | <project name>_Dev_TEST | dev-test.Jenkinsfile | dev-test.properties | dev |
| release | Pipeline | <project name>_Release_TEST | release-test.Jenkinsfile | release-test.properties | release |
| master | Pipeline | <project name>_Prod_TEST | prod-test.Jenkinsfile | prod-test.properties | master |

In your project repository, you will have this structure:

```
jenkins/  
    common-test.properties  
    dev-test.Jenkinsfile  
    dev-test.properties  
    release-test.Jenkinsfile  
    release-test.properties  
    prod-test.Jenkinsfile  
    prod-test.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-test.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
