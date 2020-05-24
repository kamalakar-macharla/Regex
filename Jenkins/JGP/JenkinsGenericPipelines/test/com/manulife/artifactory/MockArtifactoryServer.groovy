package com.manulife.artifactory

class MockArtifactoryServer {
    def script
    def credentialsId
    def buildInfo

    def upload(def spec) {
        return buildInfo
    }

    def publishBuildInfo(def buildInfo) {
        script.logger.info("Publishing build info \"" + buildInfo + "\"")
    }
}