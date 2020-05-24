package com.manulife.util.notifications

/**
 *
 * Responsible to send notifications by email or Slach channel.
 *
 **/
class NotificationsSender implements Serializable {
    Script scriptObj
    Properties pipelineParams

    NotificationsSender(Script scriptObj, Properties pipelineParams) {
        this.scriptObj = scriptObj
        this.pipelineParams = pipelineParams
    }

    def send(def message = '') {
        scriptObj.logger.info('Sending Notifications...')
        def theMessage = message.trim()

        def buildStatus = "${scriptObj.currentBuild.currentResult ?: 'COMPLETED'}"
        def buildStatusWithMessage = buildStatus + (theMessage ? " - ${theMessage}" : '')

        if (pipelineParams.emailJenkinsNotificationsTo) {
            emailNofitication(buildStatusWithMessage)
        }

        if (pipelineParams.slackTokenCredentialID) {
            slackNotification(buildStatus, buildStatusWithMessage)
        }
    }

    private emailNofitication(def buildStatusWithMessage) {
        scriptObj.emailext body: '''${SCRIPT, template="groovy-html.template"}''',
                           mimeType: 'text/html',
                           subject: "[Jenkins] ${buildStatusWithMessage} ${scriptObj.env.JOB_BASE_NAME} - Build# ${scriptObj.env.BUILD_NUMBER}",
                           to: "${pipelineParams.emailJenkinsNotificationsTo}",
                           replyTo: 'no_reply@manulife.com',
                           recipientProviders: [[$class: 'CulpritsRecipientProvider']]
    }

    private slackNotification(def buildStatus, def buildStatusWithMessage) {
        def colorCode
        def resolvedBaseName = "${scriptObj.env.JOB_NAME}"
        resolvedBaseName = resolvedBaseName.replaceAll('/', ' >> ')
        resolvedBaseName = resolvedBaseName.replaceAll('%2F', '/')
        def summary = "BUILD ${buildStatusWithMessage}: ${resolvedBaseName} - #${scriptObj.env.BUILD_NUMBER} (<${scriptObj.env.BUILD_URL}|Open>)\n Branch: ${scriptObj.env.GIT_BRANCH}"

        if (buildStatus == 'STARTED') {
            colorCode = '#FFFF00'
        }
        else if (buildStatus.startsWith('SUCCESS')) {
            colorCode = '#38A749'
        }
        else if (buildStatus == 'UNSTABLE') {
            colorCode = '#F4F142'
        }
        else {
            colorCode = '#FF0000'
        }

        scriptObj.withCredentials([scriptObj.string(credentialsId: "${pipelineParams.slackTokenCredentialID}", variable: 'tokenid')]) {
            scriptObj.slackSend(color: colorCode,
                                channel: pipelineParams.slackChannel,
                                token: scriptObj.tokenid,
                                message: summary)
        }
    }
}