package com.manulife.project

import com.manulife.logger.Level
import com.manulife.util.Shell

class ProjectConfigurationService {
    private final Script scriptObj

    ProjectConfigurationService(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    String getSquadName(String gitlabProjectId) {
        try {
            String squadName = 'N/A'
            if (gitlabProjectId) {
                String url = 'https://project-configuration-service-tst.apps.cac.preview.pcf.manulife.com/api/project'

                scriptObj.withCredentials([scriptObj.string(credentialsId: 'JENKINS_AUDIT_TOKEN', variable: 'JENKINS_AUDIT_TOKEN')]) {
                    String repsonse = Shell.quickShell(scriptObj,
                                                        "curl --location --request GET \"${url}\" \
                                                        --header \"Content-Type: application/json\" \
                                                        --header \"Authorization: ${scriptObj.env.JENKINS_AUDIT_TOKEN}\" \
                                                        --data-raw \"{\\\"projectId\\\": \\\"${gitlabProjectId}\\\"}\" ",
                                                        null, false, true, Level.DEBUG)
                    scriptObj.logger.debug("ONBOARDING PROJECT RESPONSE: ${repsonse}")
                    boolean gotJson = (repsonse.take(2) == '{\"')
                    if (gotJson) {
                        def data = scriptObj.readJSON text: "${repsonse}"
                        if (data.containsKey('squadName')) {
                            squadName = data.squadName
                        }
                        data = null
                    }
                }
            }

            return squadName
        }
        catch (ex) {
            return 'N/A'
        }
    }
}