## Introduction
This pipeline can be used to deploy applications on edge nodes.

A project will typically have one pipeline per environment where we want to deploy to:
- DEV, TST, UAT, PROD

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- dev-deploy.properties
- common-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployToEdgeNode.groovy pipeline as explained on [this page](docs/deploy.md).
A typical project will have a different Jenkins job (Deployment Pipeline) for each environement where we deploy to.
But most of the properties values that will be used are the same for all those pipelines.
You can create a file called jenkins/dev-deploy.properties that will contain all those properties that are the same for all your deployment pipelines.

#### Example properties files

Let's look at an example of jenkins/dev-deploy.properties file:  
```
# Artifactory Fetch params
releaseRepo: mfc-dig-pypi
artifactoryApiToken: artifactoryAPIToken
artifactLocation: affluence_ind

# Edge Node parameters 
edge_node: azcedledged005.mfcgd.com
userCredentials: devopsEdgeNodeCred
username: shukpar
deployDirLocation: /data-01/MFCGD.COM/shukpar/
artifactExtension: *.tar.gz
applicationConfigFile:file_1,file_2,file_3

# Additional Script Support parameters
supportScriptCommand:/data-01/MFCGD.COM/shukpar/backup.sh
**note**: this script should be written and supported  by teams using this pipeline for their own projects as it will be located and run on their edge node server.
```

 

This pipelines also supports the following properties:


| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| No  | edge_node          | edge node server name. |  |  | 
| No  | username           | User name for SSH on edge node. | | "." |
| No  | userCredentials    | Combination of username and password for SSH on edge node. | | "." |
| No  | deployDirLocation  | This parameters defines the absolute path where artifacts will be deployed on edge node. |  |  |
| Yes  | artifactoryApiToken  | This parameter is the Jenkins credentials ID to the token used to pull the artifact from Artifactory |  | artifactoryAPIToken |
| Yes  | artifactExtension  | This parameter is the wild card or extension of what kind of artifact you want to deploy on edge node. |  |  |
| Yes  | artifactLocation   | This parameter defines path to the project folder in the artifactory. Exclude the artifact repo name at starting of the path (E.g. for path mfc-dig-pypi-local/affluence_ind :  exclude mfc-dig-pypi-local/ i.e. the repo name.) |  |  |
| Yes  | releaseRepo       | This parameter tells pipeline the artifactory repo where all the artifacts are located for a specific project. |  |  |
| No  | applicationConfigFile  | This parameter tells pipeline the id of the jenkins secret files that are application configuration files stored in jenkins vault. Please specify the ids of the files separetd by commas. e.g applicationConfigFile:  file_1, file_2,  file_3 |  |  |
| No  | supportScriptCommand  | This parameter tells pipeline will enables you to run a script on their deployment server after artifact is deployed(Optional)  |  |  |


##### Properties Applicability

| Property Name | App |
| ------------- | --- |
| edge_node                 | m |
| username                  | m | 
| deployDirLocation         | m | 
| artifactExtension         | m | 
| artifactoryApiToken       | o | 
| releaseRepo               | m | 
| artifactLocation          | m | 
| applicationConfigFile     | o |
| supportScriptCommand      | o | 


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
  
## Creating the Jenkins Deployment job for the development branch
In Jenkins, you create a new pipeline job.
In its configuration, all you need to do is configure the pipeline section.

1. Select 'Pipeline script from SCM' in the definition drop down box
2. Select 'Git' in the SCM drop down list
3. Enter your SSH git repository URI 
4. Select your BU service account credentials
5. In the Script Path field, enter: jenkins/dev-deploy.Jenkinsfile
6. Run the job once which will perform the rest of the job configuration for you

That's it, you don't need to perform more configuration than that in Jenkins.

Note: We are currently exploring the usage of Jenkins Job DSL to also script that part of the configuration. 

## Conclusion
That's it, you now have a Jenkins pipeline for your development branch.

A typical project will be configured this way:

| Branch Name | Jenkins Job Type | Jenkins Job Name | Jenkinsfile Name | Properties File Name | Jenkins job branches |
| ----------- | ---------------- | ---------------- | ---------------- | -------------------- | -------------------- |
| develop | Pipeline | "project_name"_Dev_Deploy | dev-deploy.Jenkinsfile | dev-deploy.properties | dev* |
| release | Pipeline | "project_name"_Tst_Deploy | tst-deploy.Jenkinsfile | tst-deploy.properties | release* |
| release | Pipeline | "project_name"_Uat_Deploy | uat-deploy.Jenkinsfile | uat-deploy.properties | release* |
| master | Pipeline | "project_name"_Prod_Deploy | prod-deploy.Jenkinsfile | prod-deploy.properties | master* |
