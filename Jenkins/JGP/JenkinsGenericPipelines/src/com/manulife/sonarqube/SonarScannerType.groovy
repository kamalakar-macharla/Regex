package com.manulife.sonarqube

// Represents the scanner type being used:  One of sonar-scanner or sonar-scanner for MSBuild
enum SonarScannerType {
    REGULAR('-D', '-Dsonar.projectKey=', '-Dsonar.projectName=', '-Dsonar.projectVersion='),
    MSBUILD('/d:', '/k:', '/n:', '/v:')

    SonarScannerType(String propertyPrefix,
                     String projectKeyAssignment,
                     String projectNameAssignment,
                     String projectVersionAssignment) {
        this.propertyPrefix = propertyPrefix
        this.projectKeyAssignment = projectKeyAssignment
        this.projectNameAssignment = projectNameAssignment
        this.projectVersionAssignment = projectVersionAssignment
    }

    private final String propertyPrefix
    private final String projectKeyAssignment
    private final String projectNameAssignment
    private final String projectVersionAssignment

    String getPropertyPrefix() {
        return propertyPrefix
    }

    String getProjectKeyAssignment() {
        return projectKeyAssignment
    }

    String getProjectNameAssignment() {
        return projectNameAssignment
    }

    String getProjectVersionAssignment() {
        return projectVersionAssignment
    }
}
