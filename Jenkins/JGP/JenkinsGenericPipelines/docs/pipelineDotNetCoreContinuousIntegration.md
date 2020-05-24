## Introduction
A project will typically have a configuration for 4 categories of Continuous Integration (CI) pipelines
- Many for the feature & fix branches
- One for the development branch
- One per release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your DotNetCore project:

1. The automated unit tests are done with XUnit
2. To publish a library or application to Artifactory (as a NuGet package) you need your project to contain a \<projectname\>.nuspec file.
  * If publishing an application, the package will have to include the publish folder which is where the "dotnet publish" command copies all the files required for that application.  Currently, the netcoreapp version in the .nuspec file must reflect the version in TargetFramework in the main .csproj file.

```XML
    <files>  
        <file src="ReportService\bin\Release\netcoreapp2.1\publish\*" target="publish"/>
    </files>
```  
  
  * If this is a microservice to be deployed on PCF, you should also include the Manifest in your nupkg this way:  

```XML
    <files>  
        <file src="ReportService\bin\Release\netcoreapp2.1\publish\*" target="publish"/>
        <file src=".\*.yml" target="publish"/>
        <file src=".\*.yaml" target="publish"/>
    </files>
```

The new pipeline doesn't support .Net Core 3.0.  Version 3.1 must be used.

The pipeline will add a SemVersion.jgp file to the Git repo of the project if it doesn't exist an assume version 0.0.1 as the starting version.
If a team wants to start from a different version the can add that file to the Git repo with the proper version number prior to running the pipeline.  The file content should only be something like 1.2.3 so \<major\>.\<minor\>.\<patch\>

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineDotNetCoreContinuousIntegration pipeline as explained on [this page](docs/ci.md).

### common-ci.properties and dev-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
hubVersionDist: INTERNAL
hubFailOnSeverities: CRITICAL,BLOCKER,MAJOR
 
sonarQubeProjectVersion: 0.1
 
testProjectName: TravelB2C.Tests
projectName: TravelB2C
projectDeliverableName: TravelB2C

artifactoryDeploymentPattern: *.nupkg
increasePatchVersion: true

emailJenkinsNotificationsTo: francois_ouellet@manulife.com; john_doe@manulife.com;  
```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
hubVersionPhase: DEVELOPMENT
deploymentJenkinsJobName: : Aff_Affinity_Projects/Aff_PCIMFTI/Aff_PCIMFTI_frontend_B2C_Dev_Deploy
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
```

For the same project, the jenkins/release-ci.properties file would look like this instead:

```properties
hubVersionPhase: RELEASE
deploymentJenkinsJobName: Aff_Affinity_Projects/Aff_PCIMFTI/Aff_PCIMFTI_frontend_B2C_Release_Deploy
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

This pipelines also supports the following properties:

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | projectRootFolder | Name of the folder that contains the solution file (.sln) is not in the root folder | | "." |
| Yes | Yes | projectName | DotNetCore project folder name |  | N/A |
| Yes | No | runtimeProjects | Comma-separated projects within the same Gitlab project that are covered by tests | MainProject, SideProject | null (use projectName to limit the coverage) |
| Yes | No | testProjects | Comma-separated projects within the same Gitlab project that need exclusion from coverage in tests | MainProject.Tests, SideProject.Tests | null (exclude testProjectName from the coverage)  |
| Yes | No | testProjectName | DotNetCore test project folder name | Can be left empty of there is no test project | null |
| No | No | increasePatchVersion | Indicates if this pipeline should increase the project's patch version on each build. | true or false | false |
| Yes | No | publishApplication | Set to "true" if this is project builds/packages an application as opposed to a library.  This will male the pipeline call dotnet publish and package the application in a nuget package. | true or false | false |
| No | No | deploymentJenkinsJobName | Path  + Name of the deployment Jenkins pipeline to be triggered after this pipeline. | Jenkins deployment job path + name | null |

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
| feature | Multi-branch pipeline | \<project\ name\>\_Feature\_CI | feature-ci.Jenkinsfile | feature-ci.properties | feature\* fix\* |
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
