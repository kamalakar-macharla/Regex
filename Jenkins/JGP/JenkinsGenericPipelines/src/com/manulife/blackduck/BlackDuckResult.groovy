package com.manulife.blackduck

/**
 *
 * Represents the result of a BlackDuck scanner execution.
 *
 **/
class BlackDuckResult implements Serializable {
    String message = "Project status UNKNOWN.  BlackDuck wasn't called."
    String exitCodeType = "UNKNOWN"
    int exitCode = 0
    boolean governanceGatePassed
}