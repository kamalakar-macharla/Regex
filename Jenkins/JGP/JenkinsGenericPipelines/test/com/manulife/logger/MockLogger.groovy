package com.manulife.logger

import com.manulife.logger.Level

class MockLogger {
//    def realLogger
    def buffer
    Level level

    MockLogger(def script) {
//        this.realLogger = new Logger(script, Level.INFO)
        this.buffer = new StringBuffer()
    }

    def trace(def message) {
//        realLogger.trace(message)
        buffer <<= message + "\n"
    }

    def trace(def message, Closure cl) {
//        realLogger.trace(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }

    def debug(def message) {
//        realLogger.debug(message)
        buffer <<= message + "\n"
    }

    def debug(def message, Closure cl) {
//        realLogger.debug(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }

    def info(def message) {
//        realLogger.info(message)
        buffer <<= message + "\n"
    }

    def info(def message, Closure cl) {
//        realLogger.info(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }

    def warning(def message) {
//        realLogger.warning(message)
        buffer <<= message + "\n"
    }

    def warning(def message, Closure cl) {
//        realLogger.warning(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }

    def error(def message) {
//        realLogger.error(message)
        buffer <<= message + "\n"
    }

    def error(def message, Closure cl) {
//        realLogger.error(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }

    def fatal(def message) {
//        realLogger.fatal(message)
        buffer <<= message + "\n"
    }

    def fatal(def message, Closure cl) {
//        realLogger.fatal(message)
        buffer <<= message + "\n"
        if(cl) {
            cl()
        }
    }
}