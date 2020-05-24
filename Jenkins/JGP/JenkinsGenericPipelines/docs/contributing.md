# Introduction

You are facing a bug in the Jenkins Generic Pipelines or would like to improve them?

The first step is always to get in touch with the [Jenkins Generic Pipelines team](http://azuwvdsstmcd02.mfcgd.com:23860/display/DG/Jenkins+Pipelines) and have a short discussion:
* to see if this is indeed a bug or a configuration issue in your project
* to see if the missing feature is already in the backlog or not and if it is aligned with the Product Owner vision

If the conclusion is that something has to be modified in the Jenkins Generic Pipelines, you can add a JIRA item to the [Jenkins Generic Pipelines Backlog](https://dsjira.manulife.com:8443/secure/RapidBoard.jspa?rapidView=328&projectKey=JGP&view=planning.nodetail) where you explain what needs to be addressed and the expected behavior.  It will be reviewed and prioritized by the Product Owner and (assuming it was discussed previously and approved) eventually addressed by the project team.

Then, you have 2 options:
* Wait for the Jenkins Generic Pipelines Team to address the item
* You roll-up your sleeves and work on that item yourself and contribute to the success of our community!

# Source Code Management

![](docs/images/JGP-Project-GitFlow.png)

## Introducing new features

All new features have to be worked on in their own branch (**created from** the **master** branch).
A feature branch has to be **called "feature/description_of_what_this_feature_is_about"**.

Once the code is completed, a merge request will have to be submitted and the Jenkins Generic Pipelines will perform a code review.
You will then have to address all the identified issues (if any) before the code is merged in the master branch.

Eventually, a new official release version will be created and will include those code changes.
We don't add new features to a released version, we only fix issues in a release.

## Bug Fixes

All bug fixes have to be worked on in their own branch **created from** a **release** branch **or** the **master** branch.
A bug fixing branch has to be **called "fix/description_of_the_bug_fixed"**.

Once the code is completed, a merge request will have to be submitted and the Jenkins Generic Pipelines will perform a code review.
You will then have to address all the identified issues (if any) before the code is merged in the code base.


