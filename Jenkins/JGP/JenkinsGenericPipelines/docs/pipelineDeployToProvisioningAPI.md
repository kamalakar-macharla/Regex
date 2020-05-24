## Introduction
This pipeline can be used to deploy applications directly PCF for all environments utilizing the Provisioning API. 

### Language Support
 - Java (Maven and Gradle)
 - Node
 - Static HTML
 - DotNetCore (Requires the use of the new Artifactory packaging directory structure within the DotNet Core CI pipeline)

Before you can switch from a Concourse pipeline to use this one, you will need to follow the [Migration Checklist](https://cpcnissgwp01.americas.manulife.net:23200/display/CETES/Provisioning+API+Service+for+PCF)

### Capabilities
- all PCF environments but the production environment requires a valid job input with a change ticket that will be checked in Service Now
- deploy application (blue/green built in)
- create PCF marketplace services
- start, stop, restart, delete applications
- will automatically read the manifest file from the provided *manifestFileName* parameter and auto fill the values (application name, buildpack) 
- more to come... 

A project will typically have one pipeline per environment where we want to deploy to:
- DEV, TST, UAT, PROD

## Configuring one Jenkins Pipeline
Let's look at how we would configure a Jenkins pipeline for the development branch / environment.

The first step is to add 3 files in your project's jenkins folder (in GitLab):
- dev-deploy.Jenkinsfile
- dev-deploy.properties
- common-deploy.properties

### dev-deploy.Jenkinsfile
You will leverage the pipelineDeployToProvisoningAPI pipeline 

#### Example properties files
The jenkins/dev-deploy.properties file for the same project could look like this:
```properties
org: CDN-CAC-DEV
space: CDT-EXAMPLES-CAC-DEV
manifestFileName: manifest-dev.yml
servicesFileName: services-dev.json
```

The jenkins/common-deploy.properties file for the same project could look like this:
```properties
provTeamTokenCredId: ACL_APP_TEAM_CDT-EXAMPLES
releaseRepo: mfc-dig-npm
servicePrivateKey: <JenkinsCredentialID>
```

Some properties have default values, so you may not need to provide a value for them explicitly if the default is good for your job context.

##### Supported Properties

Refer to the following page(s) for details about the properties that can be configured in your properties files:
 * [Notifications (For notifications on email, slack)](docs/notifications.md)



This pipelines supports the following properties:

| Common? | Property Name | Explaination | Possible Values | Default Value |
| ------------- | ------------ | --------------- | ------------- |------------- |
| Yes | artifactoryTokenCredId | credentials id in which it is stored in jenkins to authenticate to Artifactory using the API token | artifactoryAPIToken | artifactoryAPIToken | 
| Yes | provTeamTokenCredId | credentials id in which it is stored in jenkins to authenticate to the Provisioning API for your PCF Team. See [Token Management](https://cpcnissgwp01.americas.manulife.net:23200/display/CETES/Provisioning+API+Service+for+PCF)  | ACL_APP_TEAM_CDT-EXAMPLES | null | 
| Yes | releaseRepo | artifactory repo root name where the build artifact is stored to be deployed  | mfc-dig-npm | null |
| No | foundation | PCF foundation options: USE (for Sandbox), CAC (for Preview or Operations), CAE (for CDN DR), SEA (for ASIA Preview), EAS (for ASIA Operations) | CAC | CAC |
| No | org | PCF org name that you want to push your application to | CDN-CAC-DEV | null |
| No | space | PCF space name that you want to push your application to | CDT-EXAMPLES-CAC-DEV | null |
| No | manifestFileName | manifest file name used for the pipeline defined environment | manifest-dev.yml | null |
| No | servicesFileName | service json file name used for the pipeline defined environment to create marketplace PCF services. See [Migration Checklist](https://cpcnissgwp01.americas.manulife.net:23200/display/CETES/Provisioning+API+Service+for+PCF) for more information on the json format  | TBD | null |
| No | scalingFileName | autoscaling json file name used for the pipeline defined environment to configure the auto scaling PCF service. This file can be created per environment, for example dev-scalingFile.json etc. See [Migration Checklist](https://cpcnissgwp01.americas.manulife.net:23200/display/CETES/Provisioning+API+Service+for+PCF) for more information on the json format  | TBD | null |
| No | servicePrivateKey | Jenkins credential ID for a SSH private key that will be injected during service creation | Examples-SSH | null |
| No | projectDeliverableName | Manditory only for **DotNetCore** pipelines. This is to define the application folder structure that aligns with Artifactory storage | JenkinsGenericPipeline_DotNetCore_V1.2 | null |


##### Properties Applicability

| Property Name | App |
| ------------- | --- |
| artifactoryTokenCredId    | o |
| provTeamTokenCredId       | m | 
| foundation                | o | 
| org                       | m | 
| space                     | m | 
| manifestFileName          | m | 
| servicesFileName          | o | 
| releaseRepo               | m |
| servicePrivateKey         | o |
| projectDeliverableName    | o | 


Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
