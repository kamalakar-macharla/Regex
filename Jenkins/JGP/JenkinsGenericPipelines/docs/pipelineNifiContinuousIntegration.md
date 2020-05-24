## Introduction
A project will typically have a configuration for 4 Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for the release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.
This pipeline is for a POT which is used to deploy Nifi code into Nifi QA, UAT and Prod environments. But the CI pipeline is responsible only to load Nifi code xmls, execute python scrip #1 (which in turn changes env variables)and upload them to artifactory.  

## Assumptions
This pipeline makes a few assumptions about your project.

1. 2 python scripts - one to overwrite environment variables and second to call Nifi environment.

2. Both scripts are in a repository and no change to be done in them. They are one time.

3. Second script calls Nifi-apis to deploy the code

4. Below properties need to be configured in project-config.yaml

nifi:
    Nifi_REPO: Git repository of Nifi python script

    Nifi_Code_REPO: Git repository of Nifi xml workflows code

    Nifi_branch: Branch name from where to get the python script 

    deployYAML: Yamal file name

    deployEnv: Nifi Environemnt name

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineNifiContinuousIntegration pipeline as explained on [this page](docs/ci.md).

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
 * [Git (for source-code management)](docs/git.md)
 * [GitLab (For source-code management)](docs/gitlab.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)
 
| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | No | projectType                | Type of the project   | ShellExec                    | ShellExec |
| Yes | No  | increaseVersion            | Increase Version flag                                                                       | Null by default            | Null |
| No  | No  | increasePatchVersion     | Indicates if this pipeline should increase the project's patch version on each build. | true or false                      | false |
| No  | No  | deploymentJenkinsJobName | Path  + Name of the deployment Jenkins pipeline to be triggered after this pipeline.  | Jenkins deployment job path + name | null |

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
| feature / fix | Multi-branch pipeline | project name_Feature_CI | feature-ci.Jenkinsfile | feature-ci.properties | feature* fix* |
| development | Pipeline | project name_Dev_CI | dev-ci.Jenkinsfile | dev-ci.properties | dev* |
| release | Pipeline | project name_Release_CI | release-ci.Jenkinsfile | release-ci.properties | release* |
| production | Pipeline | project name_Prod_CI | prod-ci.Jenkinsfile | prod-ci.properties | prod* |

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

