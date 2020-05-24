# Introduction

SonarQube is the standard tool for code quality scanning (automated code reviews) at Manulife.

# Supported Properties

|Common?| Property Name | Explaination | Possible Values | Default Value | 
| ------------- | ------------ | --------------- | ------------- | ------------- |
| No | sonarQubeFailPipelineOnFailedQualityGate | Specifies if the Jenkins job should fail if the code quality gate is failed | true or false | true |
| No | sonarQubeSources | Specifies directories and files to scan relatively to the project folder(s), comma-separated | Factory.cs,Exceptions,Interface,Properties | empty string (scan everything) |
| No | sonarQubeExclusions | Specifies directories and files to be excluded from scanning relatively to the project folder(s), comma-separated | Program.cs,\*\*/\*.xml,\*\*/\*.xslt,AppConfig.cs | empty string (exclude nothing) |
| No | sonarQubeProjectVersion | Specifies the project version to be used in SonarQube | The version number | null |

| Property Name | AEM | DotNet | DotNetCore | Java/Maven | NodeJS | Swift |
| ------------- | --- | ------ | ---------- | ---------- | ------ | ----- |
| sonarQubeFailPipelineOnFailedQualityGate | o | o | o | o | o | o |
| sonarQubeSources | o | o | o | o | o | o |
| sonarQubeExclusions | o | o | o | o | o | o |
| sonarQubeProjectVersion | o | m | m | o | o | o |

# Code Coverage

Depending on the language you are scanning sonar with there are specific Sonar properties and coverage reports need to be generated. In order for your coverage to be displayed in the SonarQube dashboard. 

### Node 

The following properties would need to be added to your `sonar-project.properties` file so that the coverage report can be uploaded. This assumes unit tests are run and have created a coverage folder with an LCOV coverage report. 

#### Javascript

```
sonar.language=js
# The path to the generated lcov.info file. Usually coverage/lcov.info.
sonar.javascript.lcov.reportPaths=path/to/lcov.info
```

#### Typescript

```
sonar.language=ts
# The path to the generated lcov.info file. Usually coverage/lcov.info.
sonar.typescript.lcov.reportPaths=path/to/lcov.info
```

### Python
The following files below show what is required to generate and upload a coverage report in SonarQube:

***requirements.txt*** - plugin in order to generate a coverage report
```coverage==5.0.3```

***sonar-project.properties*** - this only highlights the important property files required for coverage. You may have additional ones. ```testvenv``` is also required at the start of the path due to the virtual environment it creates within the CI.   
```
sonar.sources=testvenv/src
sonar.tests=testvenv/cddl_spark_transformations/tests
sonar.python.coverage.reportPaths=**/coverage.xml
```

***common-ci.properties*** - the command below is used from the coverage plugin that was added to requirements.txt file. It can be used exactly how it is represented below except with updating the source location to reflect your application structure.
```pythonUnitTestCommand: coverage erase && coverage run --branch --source=src -m pytest && coverage report && coverage xml -i```

### Java (Maven)
The following file below show what is required to generate and upload a coverage report in SonarQube:

***pom.xml*** - this only highlights the important property files required for coverage. You may have additional ones.
```<sonar.junit.reportPaths>target/surefire-reports</sonar.junit.reportPaths>```
