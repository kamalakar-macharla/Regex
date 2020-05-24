package com.manulife.provisioning

// TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
import groovy.json.JsonSlurper

/**
  * This class is responsible to call the provisioning api for PCF actions and to check status
  * TODO: Refactor the Provisioning API pipeline to extract logic into classes instead of within the pipeline itself
  */
class ProvisioningRun implements Serializable {
    final static int WAIT_IN_MINUTES = 20
    final static int PAUSE_IN_SECONDS = 10
    final static String API_URL = 'https://provisioning.platform.manulife.io/api/v1'
    final static String ACL_PATTERN = /^ACL_APP_TEAM_/

    static String request(Script scriptObj, Request request) {
        if (request.action == 'deploy' || request.action == 'delete') {
            return deploy(scriptObj, request)
        }
        return appstate(scriptObj, request)
    }

    static String assembleRequest(Script scriptObj, Request requestObj) {
        scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
            def team = scriptObj.pipelineParams.provTeamTokenCredId
            team = team.replaceAll(ACL_PATTERN, '')
            def request = """\
                                {\"token\":\"${scriptObj.env.SPACE_TOKEN}\",
                                \"team\": \"${team}\",
                                \"space\": \"${scriptObj.pipelineParams.space}\",
                                \"org\": \"${scriptObj.pipelineParams.org}\",
                                \"foundation\": \"${scriptObj.pipelineParams.foundation}\""""
            if (scriptObj.params.action == 'service') {
                request = assembleServiceRequest(scriptObj, request)
            }
            else if (scriptObj.request.action == 'autoscaler') {
                request = assembleScaleRequest(scriptObj, request, requestObj)
            }
            else {
                request = request + """\
                                    ,\"appName\": \"${requestObj.applicationName}\",
                                    \"ticketNumber\": \"${scriptObj.pipelineParams.ticketNumber}\"}"""
            }
            return request
        }
    }

    static String assembleScaleRequest(Script scriptObj, String request, Request requestObj) {
        def requestStr = request
        def json = scriptObj.batsh returnStdout: true, script: "cat ./${scriptObj.pipelineParams.scalingFileName}"
        requestStr = requestStr + """\
                                ,\"appName\": \"${requestObj.applicationName}\",
                                \"autoscalerDetails\": ${json},
                                \"ticketNumber\": \"${scriptObj.pipelineParams.ticketNumber}\"} """
        return requestStr
    }

    static String assembleServiceRequest(Script scriptObj, String request) {
        def requestStr = request + ',\"services\": '
        def json = scriptObj.readFile file: "./${scriptObj.pipelineParams.servicesFileName}"

        scriptObj.logger.info("${requestStr}")

        def serviceObject = scriptObj.readJSON text: "${json}"
        scriptObj.logger.info("Service Object: ${serviceObject}")
        //This check ensures property was set
        if (scriptObj.pipelineParams.servicePrivateKey != null) {
            //This check ensures there exists a possible privateKey from this project, fail build if not
            if (serviceObject.parameters.privateKey) {

                scriptObj.withCredentials([scriptObj.sshUserPrivateKey(credentialsId: scriptObj.pipelineParams.servicePrivateKey, keyFileVariable: 'PRIVATE_KEY')]) {
                
                    String tempPKStr = "${serviceObject.parameters.git.privateKey}"
                    tempPKStr = tempPKStr.substring(1, tempPKStr.length() - 1)

                    //Inject the privateKey into the service JSON
                    def keyDump = scriptObj.sh(returnStdout: true, script: "cat ${scriptObj.env.PRIVATE_KEY}")
                    keyDump = keyDump.replace('\n', '\\n')
                    json = json.replace(tempPKStr, "${keyDump}")

                }

            } else {
                scriptObj.logger.error("[ERROR]: ${scriptObj.pipelineParams.servicesFileName} does not contain privateKey!")
            }
        }
        requestStr += json + '}'

        // logging statement for testing purposes only, will reveal sensitive privateKey
        //scriptObj.logger.debug("Services Payload: ${requestStr}")
        return requestStr
    }

    static String assembleDeployRequest(Script scriptObj, Request requestObj) {
        scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
            def team = scriptObj.pipelineParams.provTeamTokenCredId
            team = team.replaceAll(ACL_PATTERN, '')
            def request = """\
                    {\"token\":\"${scriptObj.env.SPACE_TOKEN}\",
                    \"team\": \"${team}\",
                    \"space\": \"${scriptObj.pipelineParams.space}\",
                    \"org\": \"${scriptObj.pipelineParams.org}\",
                    \"foundation\": \"${scriptObj.pipelineParams.foundation}\",
                    \"appName\": \"${requestObj.applicationName}\",
                    \"ticketNumber\": \"${scriptObj.pipelineParams.ticketNumber}\""""
            if (scriptObj.params.action == 'deploy') {
                def binaryFile = scriptObj.readFile file: "./${requestObj.fileName}", encoding: 'Base64'
                request = request + """\
                    ,\"manifestFileName\": \"${scriptObj.pipelineParams.manifestFileName}\",
                    \"buildpacks\": \"${requestObj.buildpack}\",
                    \"bits\": \"${binaryFile.trim()}\"}"""
            }
            else {
                request = request + '}'
            }
            return request
        }
    }

    static HttpURLConnection sendDeployRequest(String requestStr, Request requestObj, Script scriptObj) {
        def http

        //Switch request object language back to just java so it hits the correct api endpoint
        if (requestObj.language == 'javaMaven' || requestObj.language == 'javaGradle') {
            requestObj.language = 'java'
        }

        if (scriptObj.params.action == 'delete') {
            http = new URL("${API_URL}/deploy/").openConnection() as HttpURLConnection
            http.setRequestMethod('DELETE')
        }
        else {
            http = new URL("${API_URL}/deploy/${requestObj.language}").openConnection() as HttpURLConnection
            http.setRequestMethod('POST')
        }
        http.setDoOutput(true)
        http.setRequestProperty('Content-Type', 'application/json')
        http.outputStream.write(requestStr.getBytes('UTF-8'))
        http.connect()
        return http
    }

    static HttpURLConnection sendRequest(String path, String requestStr) {
        def http
        if (path == 'service') {
            http = new URL("${API_URL}/service/").openConnection() as HttpURLConnection
        }
        else if (path == 'autoscaler') {
            http = new URL("${API_URL}/appstate/autoscaler/enable").openConnection() as HttpURLConnection
        }
        else {
            http = new URL("${API_URL}/appstate/${path}").openConnection() as HttpURLConnection
        }
        http.setRequestMethod('POST')
        http.setDoOutput(true)
        http.setRequestProperty('Content-Type', 'application/json')
        http.outputStream.write(requestStr.getBytes('UTF-8'))
        http.connect()
        return http
    }

    static String deploy(def scriptObj, Request requestObj) {
        try {
            scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
                def request = assembleDeployRequest(scriptObj, requestObj)
                HttpURLConnection http = sendDeployRequest(request, requestObj, scriptObj)
                def responseCode = http.responseCode
                if (responseCode == 200) {
                    // TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
                    def data = new JsonSlurper().parse(http.inputStream)
                    if (data) {
                        if (data.state == 'IN-PROGRESS') {
                            scriptObj.logger.info('TRANSACTION ID: ' + data.transactionID)
                            return data.transactionID
                        }
                        scriptObj.logger.error('[ERROR]: Provisioning Service did not provide the determined status')
                    }
                }
                else if (responseCode == 400) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. " +
                                           "Please double check your jenkins property files, some information may be incorrect\n ${http.getErrorStream()}\n")
                }
                else if (responseCode == 500) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. Your PCF Space Token is invalid.\n ${http.getErrorStream()}\n")
                }
                else {
                    scriptObj.logger.debug("[ERROR]: ${responseCode}-${http.getErrorStream()}")
                }
            }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to record pipeline execution.  Unexpected error(s): ${e.toString()}", e)
        }
    }

    static String appstate(def scriptObj, Request requestObj) {
        try {
            scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
                def request = assembleRequest(scriptObj, requestObj)
                HttpURLConnection http
                if (requestObj.action == 'autoscaler') {
                    http = sendRequest(requestObj.action, request)
                } else {
                    http = sendRequest(scriptObj.params.action, request)
                }
                def responseCode = http.responseCode
                if (responseCode == 200) {
                    // TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
                    def data = new JsonSlurper().parse(http.inputStream)
                    scriptObj.logger.info("${data}")
                    if (data) {
                        if (data.state == 'IN-PROGRESS') {
                            scriptObj.logger.info('TRANSACTION ID: ' + data.transactionID)
                            return data.transactionID
                        }
                        scriptObj.logger.error('[ERROR]: Provisioning Service did not provide the determined status')
                    }
                }
                else if (responseCode == 400) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. " +
                                           "Please double check your jenkins property files, some information may be incorrect\n ${http.getErrorStream()}\n")
                }
                else if (responseCode == 500) {
                    scriptObj.logger.error("[ERROR]: Provisioning API returned a ${responseCode} response code. Your PCF Space Token is invalid.\n ${http.getErrorStream()}\n")
                }
                else {
                    scriptObj.logger.error("[ERROR]: Provisoning Service did not respond properly. Returned error: ${responseCode}")
                }
            }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to record pipeline execution.  Unexpected error(s): ${e.toString()}", e)
        }
    }

    static Boolean appinfo(def scriptObj, Request requestObj) {
        try {
            scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
                def request = assembleRequest(scriptObj, requestObj)
                HttpURLConnection http = sendRequest('stats', request)
                def responseCode = http.responseCode
                if (responseCode == 200) {
                    // TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
                    def data = new JsonSlurper().parse(http.inputStream)
                    scriptObj.logger.info(data.toString())
                    if (data) {
                        if (data.state == 'SUCCESS') {
                            if (data.stats[0].state == 'RUNNING' || data.stats[0].state == 'APP_NOT_FOUND' ) {
                                scriptObj.logger.info('Application is currently running')
                                return true
                            }
                            return false
                        }
                        scriptObj.logger.error('[ERROR]: Provisioning Service did not provide the determined status')
                    }
                }
                else if (responseCode == 400) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. " +
                                           "Please double check your jenkins property files, some information may be incorrect\n ${http.getErrorStream()}\n")
                }
                else if (responseCode == 500) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code while retrieving the application status. " +
                                           "If this is a new application create than ignore. \n Error Output: ${http.getErrorStream()}\n")
                    return true
                }
                else {
                    scriptObj.logger.error("[ERROR]: Provisoning Service did not respond properly. Returned error: ${responseCode}")
                }
            }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to record pipeline execution.  Unexpected error(s): ${e.toString()}", e)
        }
    }

    static String service(def scriptObj, Request requestObj) {
      try  {
            scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
                def request = assembleRequest(scriptObj, requestObj)
                HttpURLConnection http = sendRequest(scriptObj.params.action, request)
                def responseCode = http.responseCode
                if (responseCode == 200) {
                    // TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
                    def data = new JsonSlurper().parse(http.inputStream)
                    if (data) {
                        if (data.state == 'IN-PROGRESS') {
                            scriptObj.logger.info('SERVICE TRANSACTION ID: ' + data.transactionID)
                            return data.transactionID
                        }
                        scriptObj.logger.error('[ERROR]: Provisioning Service did not provide the determined status')
                    }
                }
                else if (responseCode == 400) {
                    scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. " +
                                           "Please double check your jenkins property files, some information may be incorrect\n ${http.getErrorStream()}\n")
                }
                else if (responseCode == 500) {
                    scriptObj.logger.error("[ERROR]: Provisioning API returned a ${responseCode} response code. Your PCF Space Token is invalid.\n ${http.getErrorStream()}\n")
                }
                else {
                    scriptObj.logger.error("[ERROR]: Provisoning Service did not respond properly. Returned error: ${responseCode}")
                }
            }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to request services.  Unexpected error(s): ${e.toString()}", e)
        }
    }

    static String status(Script scriptObj, String id) {
        scriptObj.timeout(time: WAIT_IN_MINUTES, unit: 'MINUTES') {
            try {
                return pollStatus(scriptObj, id)
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException | hudson.AbortException e) {
                scriptObj.logger.error('Provisioning get status execution was aborted by an API timeout'
                        + ' that was set for ' + WAIT_IN_MINUTES + ' minutes')
                return 'TIMEOUT'
            }
        }
    }

    static String pollStatus(Script scriptObj, String id) {
        try {
                int count = 0
                while (true) {
                    count++
                    def http = new URL("${API_URL}/events/${id}").openConnection() as HttpURLConnection
                    http.setRequestMethod('GET')
                    http.setDoOutput(true)
                    http.setRequestProperty('Content-Type', 'application/json')
                    http.connect()
                    def responseCode = http.responseCode
                    if (responseCode == 200) {
                        // TODO: Replace JsonSlurper with readJSON step because JsonSlurper results are not serializable.
                        def data = new JsonSlurper().parse(http.inputStream)
                        String status = data.state
                        if ((status != null) && (status != 'IN-PROGRESS')) {
                            scriptObj.logger.info("Provisoning API returned with: ${status}")
                            def arrEvents = data.events
                            if (status == 'FAILED') {
                                scriptObj.logger.info(
                                '###########################################################################################################\n' +
                                'Request Event(s) \n')
                                for (def event : arrEvents) {
                                    scriptObj.logger.info(event.message + '\n')
                                }
                                scriptObj.logger.info(
                                '###########################################################################################################\n')
                            }
                            else {
                                scriptObj.logger.info('Last Transaction Message: ' + arrEvents.last().message)
                            }
                            return status
                        }
                        scriptObj.logger.info("Request status: ${status}")
                        data = null
                    }
                    else if (responseCode == 400) {
                        scriptObj.logger.info("[ERROR]: Provisioning API returned a ${responseCode} response code. " +
                                               "Please double check your jenkins property files, some information may be incorrect\n ${http.getErrorStream()}\n")
                    }
                    else if (responseCode == 500) {
                        scriptObj.logger.error("[ERROR]: Provisioning API returned a ${responseCode} response code. Your PCF Space Token is invalid.\n ${http.getErrorStream()}\n")
                    }
                    else {
                        scriptObj.logger.error("[ERROR]: Unable to obtain the status of the request based on ID: ${id}. REST Service returned error: ${responseCode}")
                        return 'FAILED'
                    }
                    http = null
                    scriptObj.sleep(PAUSE_IN_SECONDS)
                }
        }
        catch (e) {
            scriptObj.logger.error("[ERROR]: Unable to execute the call to obtain the request status.  Unexpected error(s): ${e.toString()}", e)
            return 'FAILED'
        }
    }
}