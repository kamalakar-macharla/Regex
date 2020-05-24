## Introduction
A project will typically have a configuration for 4 Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for the release branch
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineAEMMavenContinuousIntegration pipeline as explained on [this page](docs/ci.md).

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
#BLACKDUCK CONFIGURATIONS
# Hub
hubVersionDist:INTERNAL
hubExclusionPattern: /Nothing/To/Exclude/
hubFailOnSeverities: CRITICAL

# NOTIFICATION - SLACK
slackChannel: aem-devops-releases
slackTokenCredentialID: SLACK-TOKEN-ENT-CET

# Artifactory
artifactoryInstance:Artifactory-Global-Prod
artifactoryDeploymentPattern:*.zip
releaseRepo:mfc-dig-maven-release
snapshotRepo:mfc-dig-maven-release
artifactoryCredentials:Artifactory-Generic-Account
artifactoryApiToken:artifactoryAPIToken

# Git
#gitLabAPITokenName:CEA-GITLAB-API
gitLabSSHCredentialsId:dsdevops-ssh

#groupID sub-folder where the artifact is stored.
groupId:/ca/manulife/dxp/
```
The jenkins/dev-ci.properties file for the same project could look like this:

```properties
#QUALITY PROPERTIES
sonarQubeFailPipelineOnFailedQualityGate: false
hubFailPipelineOnFailedOpenSourceGovernance: false
hubVersionPhase: DEVELOPMENT
hubVersionDist: INTERNAL

#BUILD COMMANDS
coreBuildCommand:mvn -f pom.xml clean install 
uiBuildCommand:mvn -f ui.apps/pom.xml clean install 

coreDeployCommand:mvn -f pom.xml clean install -PautoInstallPackage  -Dcrx.host=${AEM_AUTHOR_CREDENTIALS_USR} -Dcrx.password=${AEM_ADMIN_CREDENTIALS_PSW} -e
uiDeployCommand:mvn -f ui.apps/pom.xml content-package:install -Dcrx.host=${AEM_PUBLISHER_CREDENTIALS_USR} -Dcrx.password=${AEM_ADMIN_CREDENTIALS_PSW} -Dcrx.port=4503 -e

#Credentials
AEMAdminCredentials: AEM-DEV2-ADMIN
AEMAuthorCredentials: AEM-DEV2-Author
AEMPublisherCredentials: AEM-DEV2-Publisher

#RELEASE JOB TODO
deploymentJenkinsJobName: Dig_DigitalSolutions/Dig_AEM/Dig_Global/Dig_AEM_Dispatcher_DEV_Clear_Cache

# Fortify
fortifyGating:false
fortifyTriggers:do_not_use

#project
projectRootFolder: ui.apps
mavenSettingsFileName: settings.xml
mavenPOMRelativeLocation: pom.xml

#AEMExecuteMode holds dev, qa or stage values.
AEMExecuteMode: dev

# Maven
#mavenBuildGoal:clean install
mavenTestGoal:clean package
#Indicates if this pipeline should increase the project's patch version on each build. (true/false)
increasePatchVersion:true
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
| Yes | No | appFEComponentGitLocation | Git SSH URL to pull in front end component to the build | SSH URL ||
| No | No | appFEComponentGitBranch | Front end component git branch to use | develop | master |
| Yes | No | appFEScriptsSource | Front end scripts component location on the source | ./fe/script/location ||
| Yes | No | appFEScriptsTarget | Front end scripts component location on the target | ./fe/script/location ||
| Yes | No  | appFEStylesSource | Front end styles component location on the source | ./fe/styles/location ||
| Yes | No | appFEStylesTarget | Front end styles component location on the target | ./fe/styles/location ||
| No | Yes | AEMAdminCredentials | AEM Credentials and host ips that are injected into the build commands  | AEM-DEV-ADMIN ||
| No | Yes | AEMAuthorCredentials | AEM Credentials and host ips that are injected into the build commands | AEM-DEV-Author-6.3 ||
| No | Yes | AEMPublisherCredentials | AEM Credentials and host ips that are injected into the build commands | AEM-DEV-Publisher-6.3 ||
| No | No | AEMPublisher2Credentials | AEM Credentials and host ips that are injected into the build commands | | null|
| Yes | Yes | coreBuildCommand | AEM build command for the core component of the maven project. This also takes the credentials depending on the environment. See AEM*Credentials | mvn command ||
| Yes | Yes | uiBuildCommand | AEM build command for the UI component of the maven project. This also takes the credentials depending on the environment. See AEM*Credentials | mvn command ||
| Yes | No | uiBuild2Command | AEM build command for the UI component of the maven project. This also takes the credentials depending on the environment. See AEM*Credentials | mvn command ||
|No|Yes|coreDeployCommand|This property holds the core deploy command.|||
|No|Yes|uiDeployCommand|This property holds ui deploy command.|||
|No|No|uiDeploy2Command|This property holds ui2 deploy command.|||
| No | No | deploymentJenkinsJobName | Name of the Jenkins job to be called if this job completes successfully.  This can be used to trigger a deployment pipeline job after the CI pipeline is completed. | | |
| Yes | No |artifactoryInstance|This property holds artifactory instance name. All artifacts will be stored on this instance. |Artifactory-Global-Prod||
| Yes | No |artifactoryDeploymentPattern|This property is used to define artifact pattern like zip file.|*.zip||
| Yes | No |releaseRepo|This property hold the release repository name.|mfc-dig-maven-release-local||
| Yes | No |snapshotRepo|This property holds snapshot repository name.|mfc-dig-maven-snapshot-local||
| Yes | No |artifactoryCredentials|This property holds the jenkins artifactory credentials.|Artifactory-Generic-Account||
| Yes | No |groupId|Sub-folder where the artifact is stored.|/ca/manulife/dxp/|null|
|No|No|mavenSettingsFileName|This property holds maven setting.xml file name.|settings.xml||
|No|No|mavenPOMRelativeLocation|This property holds the relative path of pom.xml file. Generally its relative path will be just pom.xml.|pom.xml||
|No|Yes|AEMExecuteMode|This property holds dev, qa and stage values. Depending on the value proper zip file will be considered while deployment. |dev, qa , stage||
|No|No|mavenTestGoal|This property will hold the maven test goal, like clean package.|clean package||
|No|Yes|increasePatchVersion|Indicates if this pipeline should increase the project's patch version on each build. (true/false)|true/false||

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

| Branch Name | Jenkins Job Type | Jenkins Job Name | CI Jenkinsfile Name | CI Properties File Name | CD Jenkinsfile Name | CD Properties File Name | Promotion Jenkinsfile Name | Promotion Properties File Name |Jenkins job branches |
| ----------- | ---------------- | ---------------- | -------------------- | -------------------------- | ------------------------------ | -------------------------- | -------------------------- | -------------------------- | ------------------------ |
| develop | Pipeline | <project name>_Dev_CI | dev-ci.Jenkinsfile | dev-ci.properties | dev-cd.Jenkinsfile | dev-cd.properties |  |  | dev* |
| qa | Pipeline | <project name>_QA_CI | qa-ci.Jenkinsfile | qa-ci.properties | qa-cd.Jenkinsfile | qa-cd.properties | dev-promotion.Jenkinsfile | dev-promotion.properties | qa* |
| tag release | Pipeline | <project name>_Stage_CI | stage-ci.Jenkinsfile | stage-ci.properties |  stage-cd.Jenkinsfile | stage-cd.properties  | release-promotion.Jenkinsfile | release-promotion.properties | stage* |

In your project repository, you will have this structure:

```
jenkins/  
    common-ci.properties
    dev-ci.Jenkinsfile
    dev-ci.properties
    qa-ci.Jenkinsfile
    qa-ci.properties
    stage-ci.Jenkinsfile
    stage-ci.properties
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-ci.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
