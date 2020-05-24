## Introduction
A project will typically have a configuration for 4 categories of Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for release, qa and uat branches
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your NodeJS project:

1. The project contains its sonar-project.properties file in its root folder
2. If you package your application and upload to Artifactory, make sure that you configure your package.json properly.

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineNodeContinuousIntegration pipeline as explained on [this page](docs/ci.md).

### common-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
nodeBuildCommand: npm install
nodeUnitTestCommand: npm run unit-test
gitLabAPITokenName: GitLabApiTokenText
hubExclusionPattern: /Nothing/To/Exclude/
hubVersionDist: INTERNAL
hubFailOnSeverities: CRITICAL,BLOCKER,MAJOR
slackDomain: ceaall
slackTokenCredentialID: Slack-Token-CEA-ALL
slackChannel: devops-releases
```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
hubVersionPhase: DEVELOPMENT  
promoteAndDeployJenkinsJobName: Example_Node_Dev_Deploy
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
```

For the same project, the jenkins/release-ci.properties file would look like this instead:

```properties
hubVersionPhase: RELEASE  
promoteAndDeployJenkinsJobName: Example_Node_Release_Deploy
sonarQubeFailPipelineOnFailedQualityGate: true
hubFailPipelineOnFailedOpenSourceGovernance: true
nodePackageCommand: npm pack
increaseVersion: patch
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
| Yes | No | projectType              | Project Type | NodeJS, ReactJS, Static | NodeJS |
| Yes | No | nodeBuildCommand         | Node Build Command | Default build  | npm install |
| Yes | No | nodePackageCommand       | Node Package Application Command. It is recommended to use npm pack as it will package your application locally and then will get uploaded with the JGP metadata. This method is only targeted at the NodeJS project type due to the alternative build methods for the ReactJS project types. If you would like to include your package-lock within the package you can add the file attribute in your package.json. For more information visit [Build Artifact Lifecycle - Node](http://cpcnissgwp01.americas.manulife.net:23860/display/DG/Node) [package.json resource for files attribute](https://alligator.io/nodejs/package-json/). For generated web application assets, it is recommended to use the projectType of *Static*. To package properly you will need to use *nodePackageCommand: npm run build* to generate the files and tar it. | npm pack | null |
| Yes | No | releaseRepo      | If the nodePackageCommand property with a value of npm pack is being used it is required that the releaseRepo is provided. This way it will know the Artifactory repo it needs to upload to  | npm-example | N/A |
| Yes | No | nodeUnitTestCommand      | Node Unit Test Command | npm test | N/A |
| Yes | No | unitTestSecret      | Provide the Jenkins credentials ID if a secret token is required to run your tests. The secret will be stored on the root file system called token.txt for the duration of the build | JENKINS_CRED_ID | null |
| Yes | No | increasePatchVersion | This will auto increment the package.json version by a patch. It will then auto commit the version change to your GitLab repo.  | true | null |
| Yes | No | increaseBetaRelease | This will increment the package.json version by a beta version tag. Eg version tag ```0.0.0-beta.1```. It will then auto commit the version change to your GitLab repo. The dist-tag of the library will be set to ```next``` to seperate package.json module consumption. Recommended use is for Libraries [More Information](https://cpcnissgwp01.americas.manulife.net:23200/display/DG/Node+Module+Library). If increasePatchVersion is also set increaseBetaRelease will be taken as the true value. | true | null |
| No  | No | deploymentJenkinsJobName | Name of the Jenkins job to be called if this job completes successfully.  This can be used to trigger a deployment pipeline job after the CI pipeline is completed. | | |

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

> Coming Soon
> This section will be fully automated where the user would just need to provide the SSH URL and jenkins would create all the jobs needed for the flow from DEV to PROD. 

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

