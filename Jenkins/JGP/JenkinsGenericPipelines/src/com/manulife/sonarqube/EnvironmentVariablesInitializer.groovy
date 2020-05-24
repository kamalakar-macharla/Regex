package com.manulife.sonarqube

/**
 *
 * Responsible to initialize the environment variables for SonarQube server
 *
 */
class EnvironmentVariablesInitializer {
    static getSonarQubeTokenName(String environment) {
        return ('Production' == environment) ? 'SonarQubeToken' : 'SonarQubeToken_Test'
    }

    static getSonarQubeServerName(String environment) {
        return ('Production' == environment) ? 'Sonar Server (main)' : 'Sonar (UAT server)'
    }

    static getSonarQubeServerURL(String environment) {
        return ('Production' == environment) ? 'https://sonar.manulife.com' : 'https://sonar-test.manulife.com'
    }
}