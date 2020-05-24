# Introduction
It is possible to have SonarQube publish its results directly on GitLab merge requests.  

![](docs/images/merge-request-sonarqube-comment.png)

This is useful for at least 2 reasons:
- It makes the peer code reviews easier since you have that information directly on the MR.  No need to go in SonarQube.
- From an audit perspective, we can demonstrate what were the issues found by SonarQube and if they were addressed.

# Important Limitations
Jenkins doesn't officially support GitLab.  It would make more sense to use GitHub and BitBucket (which are supported out-of-the-box by Jenkins) but GitLab was mandated.
Still, there is an open-source Jenkins plugin that provides **some** integration with GitLab.
That plugin currently **can't trigger Multi-Branch Pipeline jobs** on merge request events.
So, for what's described in this page to work, **you must make sure that the destination of a merge request is a (single) Pipeline job**.

# What is does
When a developer opens a new merge request (or modifies a file that is part of that merge request), it triggers an execution of the destination CI pipeline.
In Jenkins, you will see something similar to:

![](docs/images/jenkins-build-history-mr.png)

When sonarQube is invoked as part of the Jenkins job it will publish a new message on the merge request in GitLab:

![](docs/images/merge-request-sonarqube-comment.png)

# How to Configure

## Jenkins Job
All the required Jenkins job configuration for this to work is done in your pipeline's .Jenkinsfile (e.g. dev-ci.Jenkinsfile).
Obviously, this Jenkins job must be a Continuous Integration pipelines.

You have to tell Jenkins that it should handle Merge Request events originating from GitLab.

This is achieved by providing those value in your Jenkinsfile:


```
    jenkinsJobTriggerOnMergeRequest: true,
```


## GitLab Web Hook
In GitLab, you have to configure a WebHook that will trigger the Jenkins job on push and merge requests:
- Browse to the project's GitLab repo
- Select the Settings | Integrations menu option
- In the Integrations section
  - Populate the URL of the Jenkins job (you can find this URL in Jenkins)
  - Populate the secret of the Jenkins job (also found in Jenkins)
  - Make sure the "Push Event" and "Merge Request Event" options are selected
  - Press the "Add Webhook" button

You should have something similar to this:

![](docs/images/gitlab-webhook.png)

The Jenkins job URL and secret can be found directly in the configuration of the Jenkins job to be triggered by GitLab:

![](docs/images/jenkins-gitlab-config.png)

That's it!  Now GitLab will trigger your Jenkins job on merge requests.  Your job will take that event into account.  As part of the pipeline execution it will call SonarQube and tell it to publish its results on your merge request!