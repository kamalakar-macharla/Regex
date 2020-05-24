package com.manulife.jenkins

/**
 * Exceptions thrown by com.manulife.jenkins package's classes.
 **/
class JenkinsException extends Exception {
    JenkinsException(String message, Throwable cause) {
        super(message, cause)
    }

    JenkinsException(String message) {
        super(message)
    }

    JenkinsException(Throwable cause) {
        super(cause)
    }
}