package com.manulife.versioning

/**
 * Exceptions thrown by SemVer class.
 **/
class SemVersionException extends Exception {
    SemVersionException(String message, Throwable cause) {
        super(message, cause)
    }

    SemVersionException(String message) {
        super(message)
    }

    SemVersionException(Throwable cause) {
        super(cause)
    }
}