## Introduction

All the Generic Jenkins Pipelines do support reports publishing the same way. 
At the end of the job execution, Jenkins will send publish html reports if below properties a specified. 

Currently, reports are possible to be published on the following types:
* html


## Enabling report publishing for your project
The reports are optional. 

## Properties for html reports
There are three main properties for html reports publishing and all three have to be specified for it to work properly. 
* htmlReportNames
* htmlReportFiles
* htmlReportRelativePaths


| Common? | Property Name | Explaination | Possible Values | Default Value |
| -------------| -------------| ------------- | ------------ | --------------- | ------------- |
| No | htmlReportNames | The list of names you want to tag your report in Jenkins. Could be any name you want.| Seperated by &#124; | null |
| No | htmlReportFiles | The list of files of the reports generated. Could be index.html for most framework.| Seperated by &#124; | null |
| No | htmlReportRelativePaths | The list of relative path to the project root. Could be serenity/report for serenity framework. | Seperated by &#124; | null |