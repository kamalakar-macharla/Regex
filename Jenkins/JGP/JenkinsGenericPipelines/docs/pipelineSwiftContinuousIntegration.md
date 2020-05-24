This pipeline is currently under development. For status, please refer to Jira CDTJGP board for Mobile Pipeline tag.

## Introduction
A project will typically have a configuration for 4 categories of Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for qa and uat branches

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your Swift project:

1. The proper Apple appid and provisoning profiles are already setup on the build machine in order to sign the application
2. The application is configured with HockeyApp for distribution

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineSwiftContinuousIntegration pipeline as explained on [this page](docs/ci.md).

### common-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
xcodeVersionSelectCommand: sudo xcode-select --switch /Applications/Xcode.app
xcodeWorkSpace: MobileBanking.xcworkspace
xcodeScheme: MobileBanking
xcodePath: Bank
xcodeExportPlist: ./MobileBanking/exportOptions.plist
appEnvConfigGitLocation: ssh://git@gitlab.manulife.com:2222/ds-mobile/config-system.git
cocoapodsDependenciesCommand: pod install
gitLabAPITokenName: CEA-JENKINS-API
hubExclusionPattern: /Nothing/To/Exclude/
hubVersionDist: INTERNAL
hubScans: Pods
hubFailBuildForPolicyViolations: True
slackDomain: ceaall
slackChannel: devops-releases
slackTokenCredentialID: Slack-Token-CEA-ALL
hockeyAppAPIToken: f40dbcc8f6f24951bb1e13a2b7dd05f1
hockeyAppFilePath: **/**.ipa
```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
xcodeTestSimulator: 'platform=iOS Simulator,name=iPhone 6s,OS=11.2'
appEnvConfigName: ./config-system/bank-native/dev.xcconfig
appEnvConfigMoveTo: ./MobileBanking/Config/dev.xcconfig
xcodePlistFiles: MobileBanking/Info.plist MobileBankingTests/Info.plist MobileBankingUITests/Info.plist
versionBumpBranch: develop
hockeyAppAppId: e0199988ea204f7ea0dbb-DEV
```

For the same project, the jenkins/qa-ci.properties file would look like this instead:

```properties
xcodeTestSimulator: 'platform=iOS Simulator,name=iPhone 6s,OS=11.2'
appEnvConfigName: ./config-system/bank-native/qat.xcconfig
appEnvConfigMoveTo: ./MobileBanking/Config/qat.xcconfig
xcodePlistFiles: MobileBanking/Info.plist MobileBankingTests/Info.plist MobileBankingUITests/Info.plist
hockeyAppAppId: e0199988ea204f7ea0dbb3-QA
```

In this example, if your project fails the SonarQube or BlackDuck gate:
 * It will be considered as "unstable" in dev and the pipeline will still complete its execution until the end
 * It will be considered as "failed" in qa then it will not deploy the application to HockeyApp.

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
| Yes | Yes | xcodeVersionSelectCommand | Xcode version select to build your application | | null |
| Yes | No | cocoapodsDependenciesCommand | Cocoapods dependency command | pod install | null |
| Yes | No | xcodeTestSimulator | Test simulator used for unit tests | platform=iOS Simulator,name=iPhone 6s,OS=11.2 | null |
| Yes | No | xcodeWorkSpace | Xcode workspace name | | null |
| Yes | No | xcodeScheme | Xcode scheme name | | null |
| Yes | No | xcodePath | Xcode path name | | null |
| Yes | No | xcodeExportPlist | Xcode export PList for application signing | | null |
| Yes | No | appEnvConfigGitLocation | Location where configuration are stored in GitLab for different build environment | | null |
| Yes | No | appEnvConfigName | Config files to copy | | null |
| Yes | No | appEnvConfigMoveTo | Location to move the config files to the application | | null |
| Yes | No | xcodePath | Xcode path name | | null |
| Yes | No | xcodePath | Xcode path name | | null |
| Yes | No | xcodePlistFiles | Files to auto bump the version | | null |
| Yes | No | versionBumpBranch | Git branch to bump the version to | | null |
| Yes | No | releaseNotesCommand | Command to generate release notes | | null |
| Yes | No | xcodePlistFiles | Files to auto bump the version | | null |
| Yes | No | hockeyAppAPIToken | API Token for the overall application from HockeyApp | | null |
| Yes | No | hockeyAppAppId | AppId for a specific environment build in a project | | null |
| Yes | No | hockeyAppFilePath | IPA location from build | | null |
| Yes | No | hockeyReleaseNotesClass | Release notes class | | null |
| Yes | No | hockeyReleaseNotesFileName | Release notes file to use | | null |

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


In your project repository, you will have this structure:

```
jenkins/  
    dev-ci.Jenkinsfile  
    dev-ci.properties  
    feature-ci.Jenkinsfile  
    feature-ci.properties  
 
src/  
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-ci.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.

