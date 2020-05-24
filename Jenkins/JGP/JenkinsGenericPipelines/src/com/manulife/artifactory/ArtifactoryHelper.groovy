package com.manulife.artifactory

import com.manulife.util.Strings

/**
 *
 * Helper class to interact with Artifactory
 *
 **/
class ArtifactoryHelper implements Serializable {

    def server
    Script scriptObj

    static final MAX_BUILDS = [maxBuilds: 10]

    ArtifactoryHelper(def scriptObj, def server) {
        this.server = server
        this.scriptObj = scriptObj
    }

    static String getReleaseWriteRepo(Properties pipelineParams) {
        return pipelineParams.releaseWriteRepo ?: pipelineParams.releaseRepo
    }

    boolean artifactExists(def commitId, Properties pipelineParams) {
        return artifactExists(commitId,
                pipelineParams.artifactoryDeploymentPattern,
                getReleaseWriteRepo(pipelineParams))
    }

    boolean artifactExists(def commitId, def downloadPattern, String releaseRepo, String snapshotRepo = null) {
        // Remove the file just in case we already tried to download
        final FILENAME = "artifact.${commitId}.exists"

        // delete old version just in case
        scriptObj.sh "rm -fv ${FILENAME}"

        def downloadSpec =
            '''{
                "files":
                     [
            '''

        downloadSpec += getDownloadByCommitIdPattern(releaseRepo, downloadPattern, commitId, FILENAME)

        if (snapshotRepo) {
            downloadSpec += ','
            downloadSpec += getDownloadByCommitIdPattern(snapshotRepo, downloadPattern, commitId, FILENAME)
        }

        downloadSpec += ']}'


        scriptObj.logger.debug("Trying to download from Artifactory with the following downloadSpec: ${downloadSpec}")

        server.download(downloadSpec)

        def exists = scriptObj.fileExists("${FILENAME}")
        scriptObj.logger.info("The file exists in Artifactory?: ${exists}")

        // delete temporary file
        scriptObj.sh "rm -fv ${FILENAME}"
        return exists
    }

    private static getDownloadByCommitIdPattern(def repo, def downloadPattern, def commitId, def fileName) {
        return """
                {
                    "pattern": "${repo}/${downloadPattern}",
                    "props": "vcs.revision=${commitId}",
                    "flat": "true",
                    "target": "${fileName}"
                }
        """
    }

    private static getSimpleDownloadPattern(String repo, String pattern, String target, Boolean explode) {
        return """
                {
                    "pattern": "${repo}/${pattern}",
                    "flat": "true",
                    "explode": "${(explode != null) ? explode : false}",
                    "target": "${target}"
                }
        """
    }

    void uploadMavenArtifact(Properties pipelineParams,
                             def commitId,
                             def mvnSettings,
                             def sonarQubeResult,
                             def blackDuckResult,
                             def snykResult,
                             def fortifyResult,
                             def sonarQubeMsg,
                             def blackDuckMsg,
                             def snykMsg,
                             def fortifyMsg,
                             def artifactVersion) {
        def buildInfo = scriptObj.Artifactory.newBuildInfo()
        buildInfo.env.capture = true
        //TEMP FIX: REMOVED DUE TO ONGOING ISSUES WITH THE CONIGURATION OF ARTIFACTORY THAT ETS OWNS. ITS CAUSING CI PIPELINES TO FAIL
        //DUE TO BUILD INFO NOT BEING CLEANED UP AND MAINTAINING THE 10 BUILDS
        //buildInfo.retention MAX_BUILDS

        def releaseWriteRepo = getReleaseWriteRepo(pipelineParams)

        def artifactoryMaven = scriptObj.Artifactory.newMavenBuild()

        artifactoryMaven.tool = 'Maven 3.3.9'

        // Use releaseRepo for resolving all dependencies via a virtual repo
        // that hosts both external and internal modules.
        artifactoryMaven.resolver releaseRepo: pipelineParams.releaseRepo,
                snapshotRepo: pipelineParams.snapshotRepo,
                server: server

        // Upload modules to releaseRepo.  Application projects may want to
        // upload to a releaseWriteRepo.
        artifactoryMaven.deployer \
            releaseRepo: releaseWriteRepo, \
            snapshotRepo: pipelineParams.snapshotRepo, \
            server: server

        artifactoryMaven.deployer.artifactDeploymentPatterns
            .addInclude("${pipelineParams.artifactoryDeploymentPattern}")
            .addInclude('*.pom')
            .addInclude('*.yml')

        // Save (in Artifactory) all the properties used to build that artifact
        if (pipelineParams.AEMEnvironment != null) {
            artifactoryMaven.deployer.addProperty('AEMEnvironment',
            ArtifactoryProperty.fixValue(pipelineParams.AEMEnvironment))
        }
        artifactoryMaven.deployer.addProperty('git.vcs.revision',
            ArtifactoryProperty.fixValue(commitId))
        artifactoryMaven.deployer.addProperty('properties.hubVersionDist',
            ArtifactoryProperty.fixValue(pipelineParams.hubVersionDist))
        artifactoryMaven.deployer.addProperty('properties.hubVersionPhase',
            ArtifactoryProperty.fixValue(pipelineParams.hubVersionPhase))
        artifactoryMaven.deployer.addProperty('properties.releaseRepo',
            ArtifactoryProperty.fixValue(releaseWriteRepo))
        artifactoryMaven.deployer.addProperty('properties.snapshotRepo',
            ArtifactoryProperty.fixValue(pipelineParams.snapshotRepo))
        artifactoryMaven.deployer.addProperty('properties.hubExclusionPattern',
            ArtifactoryProperty.fixValue(pipelineParams.hubExclusionPattern))
        artifactoryMaven.deployer.addProperty('gating.CodeQualityGateEnabled',
            ArtifactoryProperty.fixValue(pipelineParams.sonarQubeFailPipelineOnFailedQualityGate))

        def gatingEnabled
        def gatingResult
        def gatingMsg
        if (scriptObj.env.SNYK_ACTIVE == 'TRUE') {
            gatingEnabled = pipelineParams.snykGatingEnabled
            gatingResult = snykResult.toString()
            gatingMsg = snykMsg
        }
        else {
            gatingEnabled = pipelineParams.hubFailPipelineOnFailedOpenSourceGovernance
            gatingResult = blackDuckResult.toString()
            gatingMsg = blackDuckMsg
        }

        artifactoryMaven.deployer.addProperty('gating.OpenSourceGovernanceGateEnabled',
            ArtifactoryProperty.fixValue(gatingEnabled))
        artifactoryMaven.deployer.addProperty('gating.CodeQualityResult',
            ArtifactoryProperty.fixValue(sonarQubeResult.toString()))
        artifactoryMaven.deployer.addProperty('gating.OpenSourceGovernanceResult',
            ArtifactoryProperty.fixValue(gatingResult))
        artifactoryMaven.deployer.addProperty('gating.CodeSecurityResult',
            ArtifactoryProperty.fixValue(fortifyResult.toString()))
        artifactoryMaven.deployer.addProperty('gating.CodeQualityMsg',
            ArtifactoryProperty.fixValue(sonarQubeMsg.toString()))
        artifactoryMaven.deployer.addProperty('gating.OpenSourceGovernanceMsg',
            ArtifactoryProperty.fixValue(gatingMsg))
        artifactoryMaven.deployer.addProperty('gating.CodeSecurityMsg',
            ArtifactoryProperty.fixValue(fortifyMsg.toString()))
        artifactoryMaven.deployer.addProperty('artifact.version',
            ArtifactoryProperty.fixValue(artifactVersion))
        artifactoryMaven.opts = '-DskipTests -Dmaven.test.skip=true'

        artifactoryMaven.run pom: pipelineParams.mavenPOMRelativeLocation, goals: "${mvnSettings} -B install".toString(), buildInfo: buildInfo

        server.publishBuildInfo(buildInfo)
    }

    void uploadGradleArtifact (Properties pipelineParams,
                                def commitId,
                                def gradleSettings,
                                def sonarQubeResult,
                                def blackDuckResult,
                                def snykResult,
                                def fortifyResult,
                                def sonarQubeMsg,
                                def blackDuckMsg,
                                def snykMsg,
                                def fortifyMsg,
                                def artifactVersion) {
        def buildInfo = scriptObj.Artifactory.newBuildInfo()
        buildInfo.env.capture = true
        //TEMP FIX: REMOVED DUE TO ONGOING ISSUES WITH THE CONIGURATION OF ARTIFACTORY THAT ETS OWNS. ITS CAUSING CI PIPELINES TO FAIL
        //DUE TO BUILD INFO NOT BEING CLEANED UP AND MAINTAINING THE 10 BUILDS
        //buildInfo.retention MAX_BUILDS

        String buildGradleFileName = pipelineParams.buildGradleFileName

        def artifactoryGradle = scriptObj.Artifactory.newGradleBuild()
        artifactoryGradle.tool = 'Gradle-5.5'
        artifactoryGradle.resolver server: server, repo: pipelineParams.releaseRepo

        def releaseWriteRepo = getReleaseWriteRepo(pipelineParams)
        artifactoryGradle.deployer repo: releaseWriteRepo, server: server
        artifactoryGradle.deployer.artifactDeploymentPatterns.addInclude("${pipelineParams.artifactoryDeploymentPattern}")
                                                             .addInclude('*.pom')
                                                             .addInclude('pom.xml')
                                                             .addInclude('*.yml')

        // Save (in Artifactory) all the properties used to build that artifact
        artifactoryGradle.deployer.addProperty('git.vcs.revision', ArtifactoryProperty.fixValue(commitId))
        artifactoryGradle.deployer.addProperty('properties.hubVersionDist', ArtifactoryProperty.fixValue(pipelineParams.hubVersionDist))
        artifactoryGradle.deployer.addProperty('properties.hubVersionPhase', ArtifactoryProperty.fixValue(pipelineParams.hubVersionPhase))
        artifactoryGradle.deployer.addProperty('properties.releaseRepo', ArtifactoryProperty.fixValue(pipelineParams.releaseRepo))
        artifactoryGradle.deployer.addProperty('properties.snapshotRepo', ArtifactoryProperty.fixValue(pipelineParams.snapshotRepo))
        artifactoryGradle.deployer.addProperty('properties.hubExclusionPattern', ArtifactoryProperty.fixValue(pipelineParams.hubExclusionPattern))
        artifactoryGradle.deployer.addProperty('gating.CodeQualityGateEnabled', ArtifactoryProperty.fixValue(pipelineParams.sonarQubeFailPipelineOnFailedQualityGate))

        def gatingEnabled
        def gatingResult
        def gatingMsg
        if (scriptObj.env.SNYK_ACTIVE == 'TRUE') {
            gatingEnabled = pipelineParams.snykGatingEnabled
            gatingResult = snykResult.toString()
            gatingMsg = snykMsg
        }
        else {
            gatingEnabled = pipelineParams.hubFailPipelineOnFailedOpenSourceGovernance
            gatingResult = blackDuckResult.toString()
            gatingMsg = blackDuckMsg
        }
        artifactoryGradle.deployer.addProperty('gating.OpenSourceGovernanceGateEnabled', ArtifactoryProperty.fixValue(gatingEnabled))
        artifactoryGradle.deployer.addProperty('gating.CodeQualityResult', ArtifactoryProperty.fixValue(sonarQubeResult.toString()))
        artifactoryGradle.deployer.addProperty('gating.OpenSourceGovernanceResult', ArtifactoryProperty.fixValue(gatingResult))
        artifactoryGradle.deployer.addProperty('gating.OpenSourceGovernanceMsg', ArtifactoryProperty.fixValue(gatingMsg))
        artifactoryGradle.deployer.addProperty('gating.CodeSecurityResult', ArtifactoryProperty.fixValue(fortifyResult.toString()))
        artifactoryGradle.deployer.addProperty('gating.CodeSecurityMsg', ArtifactoryProperty.fixValue(fortifyMsg.toString()))
        artifactoryGradle.deployer.addProperty('gating.CodeQualityMsg', ArtifactoryProperty.fixValue(sonarQubeMsg.toString()))
        artifactoryGradle.deployer.addProperty('artifact.version', ArtifactoryProperty.fixValue(artifactVersion))

        artifactoryGradle.usesPlugin = false
        artifactoryGradle.useWrapper = pipelineParams.useGradleWrapper.toBoolean()
        artifactoryGradle.run rootDir: '.',
                              buildFile: "${buildGradleFileName}".toString(),
                              tasks: "${gradleSettings} clean artifactoryPublish",
                              buildInfo: buildInfo

        server.publishBuildInfo(buildInfo)
    }

    void uploadArtifact (Properties pipelineParams,
                         String pattern,
                         String target,
                         def sonarQubeResult,
                         def blackDuckResult,
                         def snykResult,
                         def fortifyResult,
                         def sonarQubeMsg,
                         def blackDuckMsg,
                         def snykMsg,
                         def fortifyMsg,
                         def artifactVersion) {

        def gatingEnabled
        def gatingResult
        def gatingMsg
        if (scriptObj.env.SNYK_ACTIVE == 'TRUE') {
            gatingEnabled = pipelineParams.snykGatingEnabled
            gatingResult = snykResult.toString()
            gatingMsg = snykMsg
        }
        else {
            gatingEnabled = pipelineParams.hubFailPipelineOnFailedOpenSourceGovernance
            gatingResult = blackDuckResult.toString()
            gatingMsg = blackDuckMsg
        }

        String props = "gating.CodeQualityGateEnabled=${ArtifactoryProperty.fixValue(pipelineParams.sonarQubeFailPipelineOnFailedQualityGate)}" +
                       ";gating.OpenSourceGovernanceGateEnabled=${ArtifactoryProperty.fixValue(gatingEnabled)}" +
                       ";gating.CodeQualityResult=${ArtifactoryProperty.fixValue(sonarQubeResult.toString())}" +
                       ";gating.OpenSourceGovernanceResult=${ArtifactoryProperty.fixValue(gatingResult)}" +
                       ";gating.CodeSecurityResult=${ArtifactoryProperty.fixValue(fortifyResult.toString())}" +
                       ";gating.CodeQualityMsg=${ArtifactoryProperty.fixValue(sonarQubeMsg.toString())}" +
                       ";gating.OpenSourceGovernanceMsg=${ArtifactoryProperty.fixValue(gatingMsg)}" +
                       ";gating.CodeSecurityMsg=${ArtifactoryProperty.fixValue(fortifyMsg.toString())}" +
                       ";gating.fortifyGating=${ArtifactoryProperty.fixValue(pipelineParams.fortifyGating)}" +
                       ";artifact.version=${ArtifactoryProperty.fixValue(artifactVersion)}"
        def uploadSpec =
            """{
                "files":
                    [{
                        "pattern": "${pattern}",
                        "target": "${target}",
                        "props": "${props}"
                    }]
            }"""

        scriptObj.logger.info("Uploading with Jenkins credential ${server.credentialsId} by the spec: ${uploadSpec}")
        scriptObj.logger.debug(props)

        def buildInfo = server.upload(uploadSpec)
        // This is a list of locally uploaded artifacts,
        //      https://github.com/jfrog/build-info/blob/a08fb781d8c62758e61e056db59a4c01cb47ded6/build-info-extractor/src/main/java/org/jfrog/build/extractor/clientConfiguration/util/spec/SpecsHelper.java#L223
        //      https://www.jfrog.com/confluence/display/RTF/Working+With+Pipeline+Jobs+in+Jenkins
        // not the BuildInfo JSON, https://github.com/jfrog/build-info

        /*
            Commenting out until the calls are allowed by the sandbox security and/or the proper object is used.

            hudson.remoting.ProxyException: groovy.lang.MissingMethodException: No signature of method: org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo.getArtifacts() is applicable for argument types: () values: []
                at org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SandboxInterceptor.onMethodCall(SandboxInterceptor.java:153)

        def arts = buildInfo.getArtifacts()
        for(int i = 0; i < arts.size(); i++) {
            scriptObj.logger.debug("Artifact ${i} ${arts[i].getLocalPath()} -> ${arts[i].getRemotePath()}")
        }
        */

        server.publishBuildInfo(buildInfo)
    }

    void uploadArtifact(Properties pipelineParams,
                        String artifactoryDeploymentPattern,
                        def sonarQubeResult,
                        def blackDuckResult,
                        def snykResult,
                        def fortifyResult,
                        def sonarQubeMsg,
                        def blackDuckMsg,
                        def snykMsg,
                        def fortifyMsg,
                        def artifactVersion) {
        def releaseWriteRepo = getReleaseWriteRepo(pipelineParams)
        uploadArtifact(pipelineParams, artifactoryDeploymentPattern, "${releaseWriteRepo}",
                sonarQubeResult, blackDuckResult, snykResult, fortifyResult, sonarQubeMsg, blackDuckMsg, snykMsg, fortifyMsg, artifactVersion)
    }

    String downloadArtifact(def commitId,
                            def downloadPattern,
                            String releaseRepo,
                            String snapshotRepo = null,
                            String downloadLocation = null) {
        // Remove the file just in case we already tried to download
        final FILENAME = "artifact.${commitId}.exists/"

        // delete old version just in case
        scriptObj.sh "rm -fv ${FILENAME}"
        scriptObj.sh "rm -fv ${downloadLocation}"

        def downloadSpec =
            '''{
                "files":
                     [
            '''

        if (downloadLocation) {
            downloadSpec += getDownloadByCommitIdPattern(releaseRepo, downloadPattern, commitId, downloadLocation)
        }
        else {
            downloadSpec += getDownloadByCommitIdPattern(releaseRepo, downloadPattern, commitId, FILENAME)
        }

        if (snapshotRepo) {
            downloadSpec += ','
            if (downloadLocation) {
                downloadSpec += getDownloadByCommitIdPattern(snapshotRepo, downloadPattern, commitId, downloadLocation)
            }
            else {
                downloadSpec += getDownloadByCommitIdPattern(snapshotRepo, downloadPattern, commitId, FILENAME)
            }
        }

        downloadSpec += ']}'

        scriptObj.logger.debug("Trying to download from Artifactory with the following downloadSpec: ${downloadSpec}")

        server.download(downloadSpec)

        def exists = scriptObj.fileExists("${FILENAME}")
        scriptObj.logger.info("The file exists in Artifactory?: ${exists}")
        return "${FILENAME}"
    }

    static void downloadArtifactByCLI(Script scriptObj, String filter, String artifactFilter, String destinationFileName, boolean commitVersion) {
        def cmd = "jfrog rt dl ${artifactFilter} ${destinationFileName} --flat=\"true\" "
        if (filter == '' && commitVersion == false) {
            cmd += " --sort-by=\"created\" --sort-order=\"desc\" --limit=\"1\""
        }
        else if (commitVersion == true) {
            cmd += " --props=\"artifact.version=\"${filter}"
        }
        else if (commitVersion == false && filter != '') {
            cmd += " --props=\"vcs.revision=\"${filter}"
        }
        scriptObj.batsh "$cmd"
    }

    String downloadWrittenArtifact(def commitId, def downloadPattern, Properties pipelineParams) {
        def releaseWriteRepo = getReleaseWriteRepo(pipelineParams)
        return downloadArtifact(commitId, downloadPattern, releaseWriteRepo)
    }

    String simpleDownload(String repo, String pattern, String target, Boolean explode = false) {
        def downloadSpec =
            '''{
                "files":
                     [
            '''

        downloadSpec += getSimpleDownloadPattern(repo, pattern, target, explode)
        downloadSpec += ']}'
        def authDescr = server.credentialsId ?
            "using Jenkins credentials ID \"${server.credentialsId}\""
            : 'anonymously'
        scriptObj.logger.debug("""
Trying to download from Artifactory ${authDescr} with the following downloadSpec: ${downloadSpec}".

The current design of Artifactory matches the search pattern against only
locally downloaded files.  This prevents it from finding newer external
versions.  In case the cache is flushed, it will fail to find any version.
Only direct repositories such as jcenter-cache or maven-remote-cache can be
used to download external files, and only when the cache has them.  Instead of
using the Artifactory plugin, downloading a direct link such as

    https://artifactory.platform.manulife.io/artifactory/maven-remote/org/flywaydb/flyway-commandline/5.2.4/flyway-commandline-5.2.4-macosx-x64.tar.gz

will fill the cache automatically.

    https://www.jfrog.com/jira/browse/RTFACT-8639
""")
        server.download(downloadSpec)
        if (scriptObj.fileExists(target)) {
            return target
        }

        return null
    }

    static boolean getEnvGatingResults(Script scriptObj, Properties pipelineParams, ArtifactGovernance artifact) {
        //Get deployment environment from properties files name
        String envFileName = scriptObj.propertiesFileName
        def returnedEnvironment = Strings.regexMatchReturn(envFileName, /^(dev|uat|prod|tst)/, scriptObj)

        //Setup gating flags and check scan results
        def gatingFlag = 0
        def sonarGatingFlag = 0
        def fortifyGatingFlag = 0
        def blackduckGatingFlag = 0
        def snykGatingFlag = 0

        if (artifact.sonarQubeScanResult != true) {
            sonarGatingFlag = -1
            scriptObj.logger.warning('[WARNING] -- Artifact failed to pass the SonarQube Scan')
        }
        if (artifact.blackDuckScanResult != true) {
            blackduckGatingFlag = -1
            scriptObj.logger.warning('[WARNING] -- Artifact failed to pass the BlackDuck Scan')
        }
        if (artifact.snykScanResult != true) {
            snykGatingFlag = -1
            scriptObj.logger.warning('[WARNING] -- Artifact failed to pass the Snyk Scan')
        }
        if (artifact.fortifyScanResult != true) {
            fortifyGatingFlag = -1
            scriptObj.logger.warning('[WARNING] -- Artifact failed to pass the Fortify Scan')
        }

        //Evaluate gating flag from all the scan results
        gatingFlag = sonarGatingFlag + fortifyGatingFlag + blackduckGatingFlag + snykGatingFlag

        //Check the gating flag against the environment being deployed to
        if (returnedEnvironment != null) {
            if (returnedEnvironment == 'dev') {
                scriptObj.logger.warning("Artifact gating is automatically turned off for the ${returnedEnvironment} environment")
                return true
            }
            if (returnedEnvironment == 'tst') {
                if (gatingFlag < 0) {
                    scriptObj.logger.warning("[WARNING] -- ${returnedEnvironment}: Using the same artifact on higher environments will fail the pipleine")
                }
                else {
                    scriptObj.logger.info('Artifact passed the Quality gate')
                }
                return true
            }
            if (returnedEnvironment == 'uat' || returnedEnvironment == 'prod') {
                if (pipelineParams.gatingOverride.equalsIgnoreCase('true')) {
                    scriptObj.logger.warning('[WARNING] -- You are using the gating override for this deployment')
                    artifact.deploymentOverride = true
                    return true
                }

                if (gatingFlag < 0) {
                    scriptObj.logger.error("Pipeline failed to deploy artifact at -- ${returnedEnvironment}. Artifact failed to pass the Quality Gate Stanadards")
                    return false
                }

                scriptObj.logger.info('Artifact passed the Quality Gate Standards')
                return true
            }
        }
        else {
            scriptObj.logger.info('Environment Specified is not proper, make sure property files name is (dev/prod/tst/uat)-deploy.properties')
            return false
        }
    }

    static ArtifactGovernance updateRequestGatingResults(Script scriptObj, Properties pipelineParams, String data, ArtifactGovernance artifact) {
        def metaData = Strings.unwrapMultiLine(data)
        // Remove first and last char cus the entire json is in an "array"
        def metaDataObj = scriptObj.readJSON text: data.substring(1, data.length() - 1)
        artifact.artifactInfo = parseArtifactPath(metaDataObj.path)
        //Update artifact governance object with Sonarqube results
        if (metaData.contains('PASSED the Code Quality Gate') || metaData.contains('\"gating.CodeQualityResult": [ \"true\" ]')) {
            artifact.sonarQubeScanMsg = 'PASSED - Artifact passed the Sonarqube scan'
            artifact.sonarQubeScanResult = true
        }
        else if (metaData.contains('FAILED the Code Quality Gate') || metaData.contains('\"gating.CodeQualityResult\": [ \"false\" ]')) {
            if (metaData.contains('''"gating.CodeQualityResult": [ "Current status UNKNOWN''')) {
                 artifact.sonarQubeScanMsg = 'UNKNOWN - Sonarqube scan was not run or the results are unknown'
            }
            else {
                artifact.sonarQubeScanMsg = 'FAILED - Artifact failed the Sonarqube scan'
            }
        }

        //Update artifact governance object with Blackduck results
        if (metaData.contains('PASSED BlackDuck Open-Source Governance Gate') || metaData.contains('\"gating.OpenSourceGovernanceResult\": [ \"true\" ]')) {
            artifact.blackDuckScanMsg = 'PASSED - Artifact passed the BlackDuck scan'
            artifact.blackDuckScanResult = true
        }
        else if (metaData.contains('FAILED BlackDuck Open-Source Governance Gate') || metaData.contains('\"gating.OpenSourceGovernanceResult\": [ \"false\" ]')) {
            if (metaData.contains('\"gating.OpenSourceGovernanceMsg\": [ \"Project status UNKNOWN')) {
                artifact.blackDuckScanMsg = 'UNKNOWN - BlackDuck scan was not run or the results are unknown'
            }
        }
        else {
            artifact.blackDuckScanMsg = 'FAILED - Artifact failed the BlackDuck scan'
        }

        //Update artifact governance object with Snyk results
        if (metaData.contains('PASSED Snyk Open-Source Governance Gate') || metaData.contains('\"gating.OpenSourceGovernanceResult\": [ \"true\" ]')) {
            artifact.snykScanMsg = 'PASSED - Artifact passed the Snyk scan'
            artifact.snykScanResult = true
        }
        else if (metaData.contains('FAILED Snyk Open-Source Governance Gate') || metaData.contains('\"gating.OpenSourceGovernanceResult\": [ \"false\" ]')) {
            if (metaData.contains('''"gating.OpenSourceGovernanceMsg": [ "Project status UNKNOWN''')) {
                artifact.snykScanMsg = 'UNKNOWN - Snyk scan was not run or the results are unknown'
            }
            else {
                artifact.snykScanMsg = 'FAILED - Artifact failed the Snyk scan'
            }
        }

        //Update artifact governance object with Fortify results
        if (metaData.contains('PASSED Code Security Gate') || metaData.contains('\"gating.CodeSecurityResult\": [ \"true\" ]')) {
            artifact.fortifyScanMsg = 'PASSED - Artifact passed the Fortify scan'
            artifact.fortifyScanResult = true
        }
        else if (metaData.contains('FAILED Code Security Gate') || metaData.contains('\"gating.CodeSecurityResult\": [ \"false\" ]')) {
            if (metaData.contains('''"gating.CodeSecurityMsg": [ "Project status UNKNOWN''')) {
                artifact.fortifyScanMsg = 'UNKNOWN - Fortify scan was not run or the results are unknown'
            }
            else {
                artifact.fortifyScanMsg = 'FAILED - Artifact failed the Fortify scan'
            }
        }

        //Get environment gating results
        def envGatingResults = getEnvGatingResults(scriptObj, pipelineParams, artifact)
        if (envGatingResults) {
            return artifact
        }

        scriptObj.error('GATING ERROR: Artifact did not satisfy the requirements specified for given environment')
    }

    static String parseArtifactPath(String artifactPath) {
        int lastIndex = artifactPath.lastIndexOf('/')
        return artifactPath.substring(lastIndex + 1)
    }

    static String getMetaData(String artifactFilter, boolean commitVersion, String paramsCommitVersion, Script scriptObj) {
        //Get metadata based on the provided commit ID or version number, if no commit ID or version is provided then it will get the metadata from the latest artifact
        def rtsCmdLine = "jfrog rt s ${artifactFilter} "

        if (paramsCommitVersion == '' && commitVersion == false) {
            return scriptObj.sh(returnStdout: true, script: rtsCmdLine + '--sort-by=\"created\" --sort-order=\"desc\" --limit=\"1\"').trim()
        }
        else if (commitVersion == true) {
            return scriptObj.sh(returnStdout: true, script: rtsCmdLine + "--props=\"artifact.version=\"${paramsCommitVersion} ").trim()
        }
        else if (commitVersion == false && paramsCommitVersion != '') {
            return scriptObj.sh(returnStdout: true, script: rtsCmdLine + "--props=\"vcs.revision=\"${paramsCommitVersion} ").trim()
        }
        return null
    }

}
