package com.manulife.sonarqube

/**
 *
 * Represents the result of a Sonar Scanner run.
 *
 **/
class SonarQubeResult implements Serializable {
    String message = "Project status UNKNOWN.  SonarQube wasn't called."
    boolean codeQualityGatePassed
    int sonarBlockerIssueCount
    int sonarMajorIssueCount
    int sonarCriticalIssueCount
}