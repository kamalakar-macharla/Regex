package com.manulife.fortify

/**
 *
 * Represents the outcome of a Fortify scanner run
 *
 **/
class FortifyResult implements Serializable {
    String message = "Project status UNKNOWN.  Fortify wasn't called."
    boolean codeSecurityGatePassed
    int fortifyIssueCount = 0
}
