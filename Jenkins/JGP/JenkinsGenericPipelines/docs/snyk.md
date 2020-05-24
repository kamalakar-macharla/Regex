# Introduction

Snyk is used by Manulife to make sure projects are compliant with our Open-Source Governance around:
 * Security vulnerabilities
 * Licensing
 * Operational risk

# Supported Properties

| Common? | Property Name | Explaination | Possible Values | Default Value |
| -------------| ------------- | ------------ | --------------- | ------------- |
| No | snykGatingEnabled | Should the pipeline fail if Snyk gating is failed? | true or false | true |

The 1st column indicates if it is recommended to include that property in your common-ci.properties file or in a branch specific one (like dev-ci.properties)

| Property Name     | AEM | Docker | DotNet Framework | DotNetCore | Go | Gradle | IOS | Java/Maven | NodeJS | Python | Swift |
| ----------------- | --- | ------ | ---------------- | ---------- | -- | ------ | --- | -----------| ------ | ------ | ----- |
| snykGatingEnabled | o   | o      | o                | o          | o  | o      | o   | o          | o      | o      | o     |

Legend:
 * m: Mandatory
 * o: Optional
 * blank: Not Applicable
