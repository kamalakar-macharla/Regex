## Introduction
A project will typically have a configuration for 4 categories of Continuous Integration (CI) pipelines
- Many for the feature & fix branches
- One for the development branch
- One per release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your DotNet project:

1. The automated unit tests are done with XUnit

## Microsoft Versioning
Microsoft has a few ways to manage versioning within your dotnet project. 
JGP has built in logic to handle the different scenarios. It detects if you have one of the 2 files types which is either nuspec or AssemblyInfo.cs. 
Based on finding one of these files the pipeline will handle reading and auto versioning your application.


## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineDotNetClassicContinuousIntegration pipeline as explained on [this page](docs/ci.md).

### common-ci.properties and dev-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
# Artifactory Configuration
artifactoryDeploymentPattern: *.nupkg
releaseRepo: example-nuget

# GitLab Configuration
gitLabAPITokenName: GitLabApiTokenText

# BlackDuck configuration
hubExclusionPattern: /Nothing/To/Exclude/
hubVersionDist: INTERNAL
hubTimeoutMinutes: 45

# SonarQube Configuration
sonarQubeProjectVersion: 0.1

# .Net project names
testProjectName: TestHarness
projectName: DotNetFrameworkPipelineExample

projectDeliverableName: DotNetFrameworkPipelineExample

# email addresses to notify
emailJenkinsNotificationsTo: stacese@manulife.com;
```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
hubVersionPhase: DEVELOPMENT
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
hubFailOnSeverities: CRITICAL
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
 * [SonarQube (for code quality)](docs/sonarqube.md)
 * [Fortify (for web application or mobile security)](docs/fortify.md)

This pipelines also supports the following properties:

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | projectRootFolder | Name of the folder that contains the solution file (.sln) is not in the root folder | | "." |
| Yes | No | testProjectName | DotNet test project folder name | Can be left empty of there is no test project | null |
| Yes | No | windowsInstallerProjects | Path from project root to the windows installer file name (.vdproj) | Can be left empty of there is no installer project | null |
| Yes | Yes | projectName | DotNet project folder name |  | N/A |
| Yes | No | runtimeProjects | Comma-separated projects within the same Gitlab project that are covered by tests | MainProject, SideProject | null (use projectName to limit the coverage) |
| Yes | No | testProjects | Comma-separated projects within the same Gitlab project that need exclusion from coverage in tests | MainProject.Tests, SideProject.Tests | null (exclude testProjectName from the coverage)  |
| No | No | increasePatchVersion | Indicates if this pipeline should increase the project's patch version on each build. | true or false | false |
| No | No | deploymentJenkinsJobName | Path  + Name of the deployment Jenkins pipeline to be triggered after this pipeline. | Jenkins deployment job path + name | null |
| Yes | No | msBuildVersion | Version of MSBuild to be used.  Defaults to "MSBuild 15" | "MSBuild 14" or "MSBuild 15" | "MSBuild 15" |
| Yes | No | sonarQubeScannerVersion | Version of SonarQube scanner to be used.  Defaults to "sonar-scanner-msbuild-4" | "sonar-scanner-msbuild-4" | "sonar-scanner-msbuild-4" |
| No | No | xunitTestFlags | Xunit flags defaulting xunitTestFlags property to "" | "-notrait" | "" |
| yes | No | buildSnapshot | Defaulting buildSnapshot property to false. Set to true if you want snapshot pre-releases created. |'false'|
| No | No | includeSymbols | includeSymbols Defaulting includeSymbols property to false. Set to true if you want symbols included with nupkgs. |'false'|
| yes | No | buildType | Defaulting buildType to Release.  Set to Debug for debug builds |'Release'|


## Creating the Jenkins Continuous Integration job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the sCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-ci.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration.

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
| feature | Multi-branch pipeline | \<project name\>\_Feature\_CI | feature-ci.Jenkinsfile | feature-ci.properties | feature\* fix\* |
| development | Pipeline | \<project name\>\_Dev\_CI | dev-ci.Jenkinsfile | dev-ci.properties | dev\* |
| release-XYZ | Pipeline | \<project name\>\_Release\_CI | release-ci.Jenkinsfile | release-ci.properties | release\* |
| production | Pipeline | \<project name\>\_Prod\_CI | prod-ci.Jenkinsfile | prod-ci.properties | prod\* |

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