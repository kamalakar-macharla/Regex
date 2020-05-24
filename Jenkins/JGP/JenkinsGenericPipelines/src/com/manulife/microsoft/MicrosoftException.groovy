package com.manulife.microsoft

/**
 * Exceptions thrown by com.manulife.microsoft package's classes.
 **/
class MicrosoftException extends Exception {
    MicrosoftException(String message, Throwable cause) {
        super(message, cause)
    }

    MicrosoftException(String message) {
        super(message)
    }

    MicrosoftException(Throwable cause) {
        super(cause)
    }
}