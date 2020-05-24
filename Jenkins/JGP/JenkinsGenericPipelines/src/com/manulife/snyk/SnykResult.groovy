package com.manulife.snyk

/**
 *
 * Represents the result of a Snyk scanner execution.
 *
 **/
class SnykResult implements Serializable {
    String message = "Project status UNKNOWN.  Snyk wasn't called."
    boolean governanceGatePassed
}