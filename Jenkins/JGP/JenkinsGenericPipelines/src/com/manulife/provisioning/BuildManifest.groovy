package com.manulife.provisioning

/*
* Helper class to read the build manifest yml file needed for app deployments to PCF
*/
class BuildManifest implements Serializable {
    static manifestYaml
    static Script scriptObj

    BuildManifest(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    static void readManifestFile() {
        def yaml = scriptObj.readFile file: "./${scriptObj.pipelineParams.manifestFileName}"
        this.manifestYaml = scriptObj.readYaml text: "${yaml}"
    }

    static String getEnvironmentVariables() {
        def envVariables = ''
        if (manifestYaml.applications.env[0]) {
            // --environmentVariables '{ "key1": "value01", "key2": "value02", "key3": "value03" }'
            for (def env : manifestYaml.env) {
                envVariables += "\"${env.key}\" : \"${env.value}\", "
            }
        }
        else {
            scriptObj.logger.info('No environment variable entry was found in the manifest file')
        }

        return envVariables
    }

    static String getRoutes() {
        def routes = ''

        if (manifestYaml.applications.routes[0]) {
            for (def route : manifestYaml.applications.routes) {
                routes += route.toString().replaceAll('\\[|\\]', '').replaceAll('route:', '')
            }
        }
        else {
            scriptObj.error('ERROR: No routes were found in the manifest file, a route is mandatory if you are deploying to PCF')
        }

        return routes
    }

    static String getSourcePath() {
        def sourcePath = 'package/'
        if (manifestYaml.applications.path[0]) {
            sourcePath += manifestYaml.path.toString().replaceAll('\\[|\\]', '')
        }
        else {
            scriptObj.logger.info('No source path was found in the manifest file')
        }

        return sourcePath
    }

    static String getBuildPack() {
        def buildPack = ''
        if (manifestYaml.applications.buildpack[0]) {
            buildPack += manifestYaml.applications.buildpack[0].toString().replaceAll('\\[|\\]', '')
        } else if (manifestYaml.applications.buildpacks[0]) {
            buildPack += manifestYaml.applications.buildpacks[0].toString().replaceAll('\\[|\\]', '')
        } else {
            scriptObj.error('ERROR: Manifest buildpack could not be detected')
        }
        return buildPack
    }

    static String getAppName() {
        def appName = ''
         if (manifestYaml.applications.name) {
            appName += manifestYaml.applications.name.toString().replaceAll('\\[|\\]', '')
        }
        else {
            scriptObj.error('ERROR: No app name was found in the manifest file, please update your manifest file and provide a app name.')
        }
        return appName
    }
}