A project will typically have a configuration for 2 categories of Continuous Integration (CI) pipelines
- One for the development branch
- One for release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your Docker pipeline that may include NodeJS or Java:

1. The project contains its sonar-project.properties file in its root folder (See full documentation [SonarQube](docs/sonarqube.md))
2. The project already exists in SonarQube. If not then please look up [SonarQube](docs/sonarqube.md)

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- common-ci.properties
- docker-type-ci.Jenkinsfile (docker-dev-ci.Jenkins, docker-release-ci.Jenkins, docker-prod-ci.Jenkins)

### docker-type-ci.Jenkinsfile

You will leverage the pipelineContinuousIntegrationDocker pipeline as explained on [this page](docs/ci.md).
Type will be tag name you will pull off artifactory.

### common-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "docker-type-ci.properties" so "docker-dev-ci.properties", "docker-release-ci.properties" and "docker-prod-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
# Artifactory Settings
artifactoryInstance: Artifactory-Global-Prod
# artifactoryCredentialsId # Default blank
releaseRepo: cdt-examples-docker 
# snapshotRepo # Default blank
# artifactoryDeploymentPattern # Default blank
# projectDeliverableName # Default blank

# SonarQube Settings
# sonarQubeFailPipelineOnFailedQualityGate true, false

# Blackduck Settings
# hubVersionPhase: DEVELOPMENT # DEVELOPMENT, RELEASED
# hubVersionDist: INTERNAL # INTERNAL, PaaS, EXTERNAL
# hubExclusionPattern # Default blank
# hubExcludedModules # Default blank
# hubFailOnSeverities CRITICAL,BLOCKER,MAJOR,MINOR,TRIVIAL
# hubLoggingLevel ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF
# hubFailPipelineOnFailedOpenSourceGovernance true, false

# Docker Method
# dockerImageName: # No default need unique name
# dockerTag: # Default is latest
# dockerFileLocation: # Default is .
# dockerArtifactoryRepo: # Default docker-local
# dockerArtifactoryURL: # Default artifactory.platform.manulife.io
# dockerLoginCredential: # Default docker-login

# Email Notifications
# emailJenkinsNotificationsTo: matthew_speers@manulife.com

# Slack Notifications
# slackDomain  # Default null
# slackChannel # Default null
# slackTokenCredentialID # Default null

```

The jenkins/docker-dev-ci.properties file for the same project could look like this:
```properties
# Docker Methed
 dockerImageName: demo # No default need unique name
dockerTag: dev # Default is latest
dockerFileLocation: . # Default is .
dockerArtifactoryRepo: docker-local # Default docker-local
dockerArtifactoryURL: artifactory.platform.manulife.io # Default artifactory.platform.manulife.io
hubVersionPhase: DEVELOPMENT  
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
```

The jenkins/docker-release-ci.properties file for the same as above (docker-dev-ci.properties) project only change will be following:
```properties
# Docker Methed
 dockerImageName: demo # No default need unique name
dockerTag: release # Default is latest
dockerFileLocation: . # Default is .
dockerArtifactoryRepo: docker-local # Default docker-local
dockerArtifactoryURL: artifactory.platform.manulife.io # Default artifactory.platform.manulife.io
```

The jenkins/docker-prod-ci.properties file for the same as above (docker-dev-ci.properties) project only change will be following:
```properties
# Docker Methed
 dockerImageName: demo # No default need unique name
dockerTag: release # Default is latest
dockerFileLocation: . # Default is .
dockerArtifactoryRepo: docker-local # Default docker-local
dockerArtifactoryURL: artifactory.platform.manulife.io # Default artifactory.platform.manulife.io
```

In this example, if your project fails the SonarQube or BlackDuck gate:
 * It will be considered as "unstable" in dev and the pipeline will still complete its execution until the end
 * It will be considered as "failed" in release and won't publish any artifacts to Artifactory or trigger the execution of a deployment pipeline.

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)
 * [BlackDuck (for open-source governance)](docs/blackduck.md)
 * [Git (for source-code management)](docs/git.md)
 * [GitLab (For source-code management)](docs/gitlab.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)
 * [Snyk (for open-source governance)](docs/snyk.md)
 * [SonarQube (For code quality)](docs/sonarqube.md)


This pipelines also supports the following properties:

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | Yes | dockerImageName         | Name of the docker image will upload to artifactory | String value  | None |
| Yes | No | dockerTag              | Name of tag  on the docker image will upload to artifactory | String Name | None |
| Yes | No | dockerFileLocation         | Location of the path for docker file. It defaults to current locations (.) | String path value  | . |
| Yes | No | dockerArtifactoryURL    | Artifactory Server (URL) address to upload docker images. It defaults to global Artifactory server *artifactory.platform.manulife.io | String URL value  | artifactory.platform.manulife.io |
| Yes | No | dockerArtifactoryRepo         | Artifactory repo name to upload. It defaults to docker-local | String name value  | docker-local |
| Yes | No | dockerLoginCredential         | It will try to login with jenkins credential ID to docker server. It defaults to docker-login | String name value  | docker-login |



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

In your project repository, you will have this structure:

```
jenkins/  
    common-ci.properties
    docker-dev-ci.Jenkinsfile  
    docker-dev-ci.properties  
    docker-release-ci.Jenkinsfile  
    docker-release-ci.properties  
    docker-prod-ci.Jenkinsfile  
    docker-prod-ci.properties  
dockerfile  
```


