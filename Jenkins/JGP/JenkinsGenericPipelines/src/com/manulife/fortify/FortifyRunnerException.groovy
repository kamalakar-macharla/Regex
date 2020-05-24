package com.manulife.fortify

/**
 * Exceptions thrown by FortifyRunner.
 **/
class FortifyRunnerException extends Exception {
    FortifyRunnerException(String message, Throwable cause) {
        super(message, cause)
    }

    FortifyRunnerException(String message) {
        super(message)
    }

    FortifyRunnerException(Throwable cause) {
        super(cause)
    }
}