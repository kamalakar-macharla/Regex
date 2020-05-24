## WARNING!!!
This pipeline is not production ready yet!

## Introduction
A project will typically have a configuration for 4 categories of Continuous Integration (CI) pipelines
- One for the feature & fix branches
- One for the development branch
- One for release, qa and uat branches
- One for the production branch

Depending on how your team decides to deal with bug fixes on releases and production you may have more branches and related Jenkins jobs.

## Limitations
This pipeline doesn't support the following feature yet:
- Code quality scanning with SonarQube
- Open-Source governance with Snyk / BlackDuck
- Security code scanning with Fortify
- Automated project versioning on minor version.  Note:  Not even sure GoLang has a standard way to support the notion of versions...
- The build server currenly can't resolve dependencies.  Include the vendor/ folder in GitLab to work around that issue.

## Important note
GoLang requires the GoLang source code to be under a src/ folder.

So, this pipeline will retrieve your project source code from GitLab and then do some folders manipulations for you.

If in GitLab you have this for your "project" repository:
* jenkins/
* golang-project/
* ...
  
The pipeline will move the "golang-project" folder under the src/challenge/ folder so that the Jenkins project workspace can be used as the GOPATH.  On disk you will have:
* jenkins/
* src/project/golang-project/

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your Go project:

1. The project contain the following 2 files that are defining its dependencies: 
  - Gopkg.lock
  - Gopkg.toml
2. Your GoLang source code is in its own folder at the root of the GitLab repo

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-ci.Jenkinsfile
- common-ci.properties
- dev-ci.properties

### dev-ci.Jenkinsfile

You will leverage the pipelineGoContinuousIntegration pipeline as explained on [this page](docs/ci.md).

### common-ci.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Continuous Integration Pipeline) for the feature, dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-ci.properties that will contain all those properties that are the same for all your CI pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-ci.properties".

#### Example properties files

Let's look at an example of jenkins/common-ci.properties file:  
```properties
projectRootFolder: myrootfolder
projectFinalRootFolder: src/git_repo_name/myrootfolder
binaryReleaseRepo: example-generic
binaryFileName: my_exe_name
testCommand: go test ./...

# see https://www.digitalocean.com/community/tutorials/how-to-build-go-executables-for-multiple-platforms-on-ubuntu-16-04
goos: linux
goarch: amd64

```

The jenkins/dev-ci.properties file for the same project could look like this:

```properties
# Nothing
```

For the same project, the jenkins/release-ci.properties file would look like this instead:

```properties
# nothing
```

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [Artifactory (for dependencies resolution and storage of your binary artifacts)](docs/artifactory.md)
 * [Git (for source-code management)](docs/git.md)
 * [GitLab (For source-code management)](docs/gitlab.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)
 * [Snyk (for open-source governance)](docs/snyk.md)

This pipelines also supports the following properties:

| Common? | Mandatory | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------- | ------------- | ------------ | --------------- | ------------- |
| Yes | Yes | projectRootFolder        | GoLang project root folder as found in GitLab |  |  |
| Yes | Yes | projectFinalRootFolder   | GoLang project root folder as it should be on disk (in the Jenkins workspace) |   |  |
| Yes | No  | dependencyCommand        | dep command to run to resolve the project dependencies | Default "dep ensure"  | dep ensure |
| Yes | Yes | goos                     | Type of OS that the binaries should be built for. See https://www.digitalocean.com/community/tutorials/how-to-build-go-executables-for-multiple-platforms-on-ubuntu-16-04 for supported values. For PCF, set to linux|  |  |
| Yes | Yes | goarch                   | Type of architecture that the binaries should be built for. See https://www.digitalocean.com/community/tutorials/how-to-build-go-executables-for-multiple-platforms-on-ubuntu-16-04 for supported values. For PCF, set to amd64 | | |
| Yes | No  | buildCommand             | Go build command for project.  Defaults to "go install" |  | go install |
| Yes | No  | testCommand              | Go test command. Defaults to null. | go test | null |
| Yes | No  | binaryFileName           | Name of the Exe produced by the build (in the bin/ folder).  Defaults to null |  | null |
| Yes | No  | binaryReleaseRepo        | Name of the Artifactory "Generic" repository where the binaries should be uploaded. Defaults to null.| Ex: example-generic | null |
| No  | No  | deploymentJenkinsJobName | Name of the deployment Jenkins job to be triggered at the end of a successful execution of this pipeline. Faults to null. |  | null |

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
| production | Pipeline | <project name>_Prod_CI | prod-ci.Jenkinsfile | prod-ci.properties | prod* |

In your project repository, you will have this structure:

```
jenkins/  
    dev-ci.Jenkinsfile  
    dev-ci.properties  
    feature-ci.Jenkinsfile  
    feature-ci.properties  
    prod-ci.Jenkinsfile  
    prod-ci.properties  
your_golang_project_folder/
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-ci.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.

