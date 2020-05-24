package com.manulife.sonarqube

import com.manulife.logger.Level
import com.manulife.util.Shell
import com.manulife.util.Strings

/**
 *
 * Facade for the SonarQube Web API: https://sonar.manulife.com/web_api/api/projects
 *
 **/
class SonarQubeWebApi {
    final static String CA_BUNDLE_FILE = 'sonar-zscaler-bundle.pem'
    final static String HTTP_VERB_GET = 'GET'
    final static String HTTP_VERB_POST = 'POST'

    /**
     *
     * Checks if a project exists in SonarQube with the specified projectKey.
     * It will create the project if it doesn't exist yet
     *
     **/
    static createProjectIfMissing(Script scriptObj, String projectKey) {
        scriptObj.logger.debug("Calling SonarQubeUtils.createProjectIfMissing() with projectKey = ${projectKey}")

        String findProjectRestApiCall = "api/projects/search?projects=${projectKey}"
        def jsonResponse = callRestAPI(scriptObj, findProjectRestApiCall, HTTP_VERB_GET)
        if (jsonResponse != null && jsonResponse.paging.total > 0) {
            scriptObj.logger.info("Project ${projectKey} already exists in SonarQube.")
            return
        }

        scriptObj.logger.info("Project ${projectKey} doesn't exist in SonarQube.  Will try to create.")

        String createProjectRestApiCall = "api/projects/create?key=${projectKey}&name=${projectKey}"
        jsonResponse = callRestAPI(scriptObj, createProjectRestApiCall, HTTP_VERB_POST)
        if (jsonResponse != null && jsonResponse.project.key == projectKey) {
            scriptObj.logger.info("Project ${projectKey} created successfully.")
        }
        else {
            scriptObj.logger.info("Unable to create ${projectKey} project.")
        }
    }

    /**
     *
     * Checks if the branch exists in SonarQube for the specified project.
     *
     **/
    static boolean branchExists(Script scriptObj, String projectName, String branchName) {
        scriptObj.logger.debug("Calling SonarQubeUtils.createTargetBranchIfMissing() with projectName = ${projectName} and branchName = ${branchName}")

        String findBranchRestApiCall = "api/project_branches/list?project=${projectName}"
        def jsonResponse = callRestAPI(scriptObj, findBranchRestApiCall, HTTP_VERB_GET)
        boolean found
        if (jsonResponse == null) {
            scriptObj.logger.info("Unable to verify if project ${projectName} contains a ${branchName} branch.")
            return false
        }

        for (branch in jsonResponse.branches) {
            if (branchName == branch.name) {
                found = true
                break
            }
        }

        if (found) {
            scriptObj.logger.info("Project ${projectName} contains a ${branchName} branch.")
        }
        else {
            scriptObj.logger.info("Project ${projectName} doesn't have a ${branchName} branch.")
        }

        return found
    }

    /**
     *
     * Calls one of the SonarQube Web API endpoints.
     *
     **/
    private static callRestAPI(Script scriptObj, String restApiCall, String httpVerb) {
        String tmpDir = scriptObj.pwd(tmp: true)

        String caBundlePath = "${tmpDir}/${CA_BUNDLE_FILE}"
        String caBundle = Strings.deBOM(scriptObj.libraryResource(resource: 'com/manulife/ssl/zscaler-curl-bundle.pem', encoding: 'UTF-8'))
        scriptObj.writeFile(file: caBundlePath, text: caBundle, encoding: 'UTF-8')

        String responsePath = "${tmpDir}/response.txt"
        String sonarQubeServerUrl = EnvironmentVariablesInitializer.getSonarQubeServerURL(scriptObj.env.SONAR_ENVIRONMENT)
        String fullRestApiCall = "${sonarQubeServerUrl}/${restApiCall}"

        String httpCode
        scriptObj.withCredentials([
            scriptObj.string(credentialsId: EnvironmentVariablesInitializer.getSonarQubeTokenName(scriptObj.env.SONAR_ENVIRONMENT),
                                  variable: 'SONAR_TOKEN')]) {
            String token = scriptObj.env.SONAR_TOKEN
            String command = "curl -X ${httpVerb} -s --cacert \"${caBundlePath}\" -u \"${token}:\" -o \"${responsePath}\" --write-out \"%{http_code}\" \"${fullRestApiCall}\""

            // -X ${httpVerb}
            scriptObj.logger.debug("Sending REST API call to SonarQube: ${fullRestApiCall}")
            httpCode = Shell.quickShell(scriptObj, command, null, false, false, Level.DEBUG).trim()
        }

        def retVal
        if ('200' == httpCode) {
            String body = scriptObj.readFile(file: responsePath, encoding: 'UTF-8').trim()
            scriptObj.logger.debug("body: ${body}")
            retVal = scriptObj.readJSON text: "${body}"
            scriptObj.logger.debug("retVal: ${retVal}")
        }
        else {
            scriptObj.logger.error("${fullRestApiCall} REST API call failed with return code '${httpCode}'.")
            scriptObj.error("The pipeline was unable to communicate with SonarQube's Web API.  " +
                            'Please verify that the SonarQube token is configured properly in Jenkins.  ' +
                            'If it is then please out to the production support team for help.')
        }

        return retVal
    }
}
