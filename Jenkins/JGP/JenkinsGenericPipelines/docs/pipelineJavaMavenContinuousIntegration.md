## Introduction
A project will typically have a configuration for 4 Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for the release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This pipeline makes a few assumptions about your Maven project.

1. If the project contains unit tests the POM.xml is configured to run the tests with junit and call JaCoCo to compute the code coverage.  Refer to the "build" section in this [example POM.xml](docs/examples/pipelineJavaMavenContinuousIntegration-POM.xml).
2. The SonarQube configuration is NOT provided with a project-settings.xml file but directly in the POM.xml file directly (in properties section).  Refer to "properties" section in this [example POM.xml](docs/examples/pipelineJavaMavenContinuousIntegration-POM.xml).

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineJavaMavenContinuousIntegration pipeline as explained on [this page](docs/ci.md).

If your application requires JDK 11 you can add an additional value to your JenkinsFile. If nothing is provided it will default to JDK 8
```
latestJava: true
```

### common-ci.properties and dev-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
releaseRepo: example-maven-release  
snapshotRepo: example-maven-snapshot  
artifactoryDeploymentPattern: *.jar
hubVersionDist: INTERNAL  
hubFailOnSeverities: CRITICAL,BLOCKER,MAJOR
```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
hubVersionPhase: DEVELOPMENT  
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
```

For the same project, the jenkins/release-ci.properties file would look like this instead:

```properties
hubVersionPhase: RELEASE  
sonarQubeFailPipelineOnFailedQualityGate: true
hubFailPipelineOnFailedOpenSourceGovernance: true
```
In this example, if your project fails the SonarQube or BlackDuck gate:
 * It will be considered as "unstable" in dev and the pipeline will still complete its execution until the end
 * It will be considered as "failed" in release and won't publish any artifacts to Artifactory or trigger the execution of a deployment pipeline.

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)
 * [BlackDuck (for open-source governance)](docs/blackduck.md)
 * [Git (for source-code management)](docs/git.md)
 * [GitLab (for source-code management)](docs/gitlab.md)
 * [Notifications (for notifications on email or Slack)](docs/notifications.md)
 * [Snyk (for open-source governance)](docs/snyk.md)
 * [SonarQube (for code quality)](docs/sonarqube.md)
 * [Fortify (for web application or mobile security)](docs/fortify.md)

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | mavenBuildGoal | Maven build goal | Your special Maven goal  | -T 2 -B clean compile |
| Yes | No | mavenTestGoal | Maven test goal | Your special Maven goal  | -B -f pom.xml test -Dmaven.test.failure.ignore=true |
| Yes | No | mavenSettingsFileName | Maven settings.xml file id | One of the files defined in your Jenkins folder Config Files section | settings.xml |
| Yes | No | mavenPOMRelativeLocation | Maven pom.xml file | You special Maven pom.xml location | pom.xml |
| No | No | increasePatchVersion | Indicates if this pipeline should increase the project's patch version on each build. | true or false | false |
| No | No | deploymentJenkinsJobName | Path  + Name of the deployment Jenkins pipeline to be triggered after this pipeline. | Jenkins deployment job path + name | null |
| No | No | projectRootFolder | Changes the Root Folder of the project | path to root folder | root folder|
| Yes | No | integrationTestReport | Publish the integration test report on Jenkins  | path to integration test report | target/failsafe-reports/\*.xml |
| Yes | No | unitTestReport | Publish the unit test report on Jenkins | path to unit test report | target/surefire-reports/\*.xml |

## Creating the Jenkins Continuous Integration job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-ci.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

## Configure GitLab to trigger your job when there are events on the repository

You want to configure GitLab to trigger your Jenkins job when someone commits code in the development branch.
To do so:

1. Connect in GitLab and browse to your project page
2. Open the "Settings" tab
3. Open the "Integrations" tab
4. You have to capture the URL to your Jenkins project.  Login into Jenkins and open your project configuration.  In the "Build Triggers" section you will see the configuration for the integration with GitLab including the "GitLab CI Service URL".  Copy that url in the GitLab URL field.
5. In the Secret Token field simply copy the value that you've put for jenkinsJobSecretToken in your dev-ci.Jenkinsfile
5. Make sure both the "Push events" and "Merge request events" options are enabled
6. Press the "Add Webhook" button

That's it, Jenkins will now trigger your job on push and merge request events!

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| feature / fix | Multi-branch pipeline | <project name>_Feature_CI | feature-ci.Jenkinsfile | feature-ci.properties | feature* fix* |
| development | Pipeline | <project name>_Dev_CI | dev-ci.Jenkinsfile | dev-ci.properties | dev* |
| release | Pipeline | <project name>_Release_CI | release-ci.Jenkinsfile | release-ci.properties | release* |
| production | Pipeline | <project name>_Prod_CI | prod-ci.Jenkinsfile | prod-ci.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-ci.Jenkinsfile  
    dev-ci.properties  
    feature-ci.Jenkinsfile  
    feature-ci.properties  
    release-ci.Jenkinsfile  
    release-ci.properties  
    prod-ci.Jenkinsfile  
    prod-ci.properties  
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-ci.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.

