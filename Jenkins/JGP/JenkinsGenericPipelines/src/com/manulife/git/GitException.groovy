package com.manulife.git

/**
 * Exceptions thrown by com.manulife.git package classes.
 **/
class GitException extends Exception {
    GitException(String message, Throwable cause) {
        super(message, cause)
    }

    GitException(String message) {
        super(message)
    }

    GitException(Throwable cause) {
        super(cause)
    }
}