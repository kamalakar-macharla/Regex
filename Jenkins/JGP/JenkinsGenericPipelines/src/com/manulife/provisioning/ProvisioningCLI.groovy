package com.manulife.provisioning

/**
  * This class is responsible for setting up and calling the provisioning CLI for PCF actions
  * At the moment this class only supports the blue/green deployment with smoke tests action
  */
class ProvisioningCLI implements Serializable {
    //CLI version
    final static String TOOL_VERSION = '0.4.0'

    static String getOperatingSystem(Script scriptObj) {
        //Get current operating system
        def operatingSystem
        if (scriptObj.isUnix()) {
            if (scriptObj.env.NODE_NAME.toLowerCase().contains('mac')) {
                operatingSystem = 'mac'
            }
            else {
                operatingSystem = 'linux'
            }
        }
        else {
            operatingSystem = 'windows'
        }

        return operatingSystem
    }

    static void configureProvisioningCLI(Script scriptObj) {
        scriptObj.withCredentials([scriptObj.string(credentialsId: scriptObj.pipelineParams.provTeamTokenCredId, variable: 'SPACE_TOKEN')]) {
            //Get operating system
            def operatingSystem = getOperatingSystem(scriptObj)

            //Download provisioning API CLI
            scriptObj.batsh "curl -s https://artifactory.platform.manulife.io/artifactory/bin-local/provisioning-cli/provisioning-cli-v${TOOL_VERSION}.${operatingSystem}.tar.bz2 | tar -xvz"
            //Configure CLI info
            scriptObj.batsh './provisioning-cli config set-api provisioning.platform.manulife.io'
            scriptObj.batsh "./provisioning-cli config set-token ${scriptObj.env.SPACE_TOKEN}"
            scriptObj.batsh "./provisioning-cli config set-org ${scriptObj.pipelineParams.org}"
            scriptObj.batsh "./provisioning-cli config set-space ${scriptObj.pipelineParams.space}"
            scriptObj.batsh "./provisioning-cli config set-foundation ${scriptObj.pipelineParams.foundation}"
        }
    }

    static String buildPushCommand(Script scriptObj, Request requestObj) {
        //Extract information from manifest file
        BuildManifest buildManifest = new BuildManifest(scriptObj)
        buildManifest.readManifestFile()
        def appName = buildManifest.getAppName()
        def buildpack = buildManifest.getBuildPack()
        def environmentVariables = buildManifest.getEnvironmentVariables()
        def routes = buildManifest.getRoutes()
        def sourcePath = buildManifest.getSourcePath()
        def framework = requestObj.language

        //Switch framework over to just java if the language type is javaMaven or javaGradle
        if (requestObj.language == 'javaMaven' || requestObj.language == 'javaGradle') {
            framework = 'java'
        }

        def pushCommand = """
                                ./provisioning-cli app push-with-smoke-test \
                                --appName \"${appName}\" \
                                --manifestFileName \"${scriptObj.pipelineParams.manifestFileName}\" \
                                --stack 'cflinuxfs3' \
                                --buildPacks \"${buildpack}\" \
                                --smokeTestScript \"${scriptObj.pipelineParams.unitTestFileName}\" \
                                --framework \"${framework}\" \
                                --routes \"${routes}\" \
                                --sourcePath \"${sourcePath}\"
                            """

        if (environmentVariables != '') {
            pushCommand += "--environmentVariables \'{${environmentVariables}}\'"
        }

        return pushCommand
    }

    static void packageApplication(Script scriptObj, Request request) {
        if (request.language == 'node') {
            def packageName = scriptObj.sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
            scriptObj.batsh "tar -xzf ${packageName}.tgz"
            scriptObj.batsh "rm ${packageName}.tgz"
            scriptObj.batsh "cat ${scriptObj.pipelineParams.manifestFileName}"
            if (scriptObj.fileExists('package/.scannerwork')) {
                scriptObj.batsh 'rm -r package/.scannerwork'
            }
            scriptObj.batsh 'ls -la package/'
            scriptObj.batsh "cp -R ${scriptObj.pipelineParams.manifestFileName} package"
            scriptObj.batsh 'cp -R .npmrc package'
        }
        else if (request.language == 'html') {
            def packageName = sh(returnStdout: true, script: '''node -p "require('./package.json').name"''').trim()
            scriptObj.batsh "tar -xzf ${packageName}.tgz"
            scriptObj.batsh "rm ${packageName}.tgz"
            scriptObj.batsh "cat package/${scriptObj.pipelineParams.manifestFileName}"
            if (scriptObj.fileExists('package/.scannerwork')) {
                scriptObj.batsh 'rm -r package/.scannerwork'
            }
            scriptObj.batsh 'ls -la package/'
        }
        else if (request.language == 'javaMaven' || request.language == 'javaGradle') {
            scriptObj.batsh "cat ${scriptObj.pipelineParams.manifestFileName}"
            scriptObj.batsh 'ls -la package/'
            scriptObj.batsh "cp -R ${scriptObj.pipelineParams.manifestFileName} package"
        }
        else if (request.language == 'dotnetcore') {
            scriptObj.batsh "cat ${scriptObj.pipelineParams.manifestFileName}"
            scriptObj.batsh "unzip ${scriptObj.publishName}.nupkg -d package"
            scriptObj.scriptObj.batsh "cp -R ${scriptObj.pipelineParams.manifestFileName} package"
            scriptObj.batsh 'cp -R package/publish/* package'
            if (scriptObj.fileExists('package/.scannerwork')) {
                scriptObj.batsh 'rm -r package/.scannerwork'
            }
            scriptObj.batsh 'ls -la package/'
        }
    }
}