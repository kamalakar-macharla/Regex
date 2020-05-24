## Introduction
A project will typically have a promotion pipeline to promote code from:
- dev to release branch
- release to production/master branch

![](docs/images/promotion-pipelines.png)

Depending on how your team decides to deal with branching you may have more branches and related Jenkins jobs.
The pipelines are flexible enough to be configured accordingly.

The promotion pipeline allows you to specify if the source and/or destination branch version number should be modified and how they should be modified:
  
  * switch from a pre-release to a release version
  * increase the minor version  (second digit in version number)
  * increase the patch version  (third digit in version number)

## Assumptions
This Jenkins Generic Pipeline makes the following assumptions about the content of your Python project:

  * parameters.json
  * setup.py
  * .bumpversion.cfg

* If you require release notes, you must have the file named release.md to be recognized by the release notes generator.
  
### dev-promotion.Jenkinsfile

You will leverage the pipelinePromotionPython pipeline as explained on [this page](docs/promotion.md).

### common-promotion.properties and dev-promotion.properties
The rest of the Generic Jenkins Pipelines configuration takes place in easy to use properties files.

A typical project will have a different Jenkins job (Promotion Pipeline) for the dev and release branches.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/common-promotion.properties that will contain all those properties that are the same for all your promotion pipelines.

Then, for what is different for each pipeline, you can configure another properties file like "dev-promotion.properties".

#### Example properties files

Let's look at an example of jenkins/common-promotion.properties file:  
```properties
onlyOneReleaseBranch: true
gitLabSSHCredentialsId: Examples-SSH
```

The jenkins/dev-promotion.properties file for the same project could look like this:

```properties
fromBranch: develop
toBranch: release
increaseFromBranchMinorVersion: true
increaseToBranchPatchVersion: false
fromSnaphotToReleaseOnToBranch: true
deploymentJenkinsJobName: Jenkins_Job_Name_Trigger
releaseNotesFlag: true
```

For the same project, the jenkins/release-promotion.properties file would look like this instead:

```properties
fromBranch: release
toBranch: master
increaseFromBranchMinorVersion: false
increaseToBranchPatchVersion: false
fromSnaphotToReleaseOnToBranch: false
deploymentJenkinsJobName: Jenkins_Job_Name_Trigger
```

In this example, the promotion from dev to release will:
  * increase the minor version in the dev branch so that work on the next release can begin
  * copy the source code from the dev branch to the release branch
  * fix the version number in the release branch so that it is not a pre-release version anymore
  * Generate Release notes in the release branch of the project repo if *releaseNotesFlag: true* is specified in properties file (optional parameter). Check out the [Release notes standards](https://cpcnissgwp01.americas.manulife.net:23200/display/CETES/Integrating+Automated+Release+Notes+Generation+in+Promotional+Pipelines) for more information on how it works. 

Refer to the following pages for details about the properties that can be configured in your properties files:
 * [GitLab (For source-code and version management)](docs/gitlabpromotion.md)
 * [Notifications (For notifications on email or Slack)](docs/notifications.md)

## Creating the Jenkins Promotion job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the sCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-promotion.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration.

## Conclusion
That's it, you now have a promotion pipeline for your development branch that will promote code to the release branch and adjust version numbers as asked.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| development | Pipeline | projectName_Promote_Dev_To_Release | dev-promote.Jenkinsfile | dev-promote.properties | dev* |
| release     | Pipeline | projectName_Promote_Release_To_Prod | release-promote.Jenkinsfile | release-promote.properties | release* |

In your project repository, you will have this structure:

```
jenkins/  
    common-promote.properties  
    dev-promote.Jenkinsfile  
    dev-promote.properties  
    release-promote.Jenkinsfile  
    release-promote.properties  
src/
```

You can follow the same process, as for dev, to create the files for the other environments.  
Obviously, you won't have to redo the work for the common-promote.properties file since it has to be defined only once.  
Then you can configure the remaining Jenkins jobs according to the table above.
