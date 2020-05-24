package com.manulife.jenkins

import com.manulife.logger.MockLogger

class MockScript extends Script {
    def unix = true
    def env = [:]
    def params = [:]
    def pipelineParams = [:]
    def exitCode
    def shRetVal
    def batRetVal
    def logger
    def currentBuild = [:]

    MockScript() {
        this.logger = new MockLogger(this)
    }

    Object run() {
        logger.info("Running...")
    }

    def echo(String message) {
        println(message)
    }

    def isUnix() {
        return unix
    }

    def ansiColor(String str, Closure cl) {
        cl()
    }

    def setCurrentResult(String currentResult) {
        currentBuild.currentResult = currentResult
    }

    def sh(def params = null) {
        return shRetVal
    }

    def bat(def params = null) {
        return batRetVal
    }
}