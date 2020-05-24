package com.manulife.gitlab

/**
 * Exceptions thrown by GitLabUtils.
 **/
class GitLabUtilsException extends Exception {
    GitLabUtilsException(String message, Throwable cause) {
        super(message, cause)
    }

    GitLabUtilsException(String message) {
        super(message)
    }

    GitLabUtilsException(Throwable cause) {
        super(cause)
    }
}