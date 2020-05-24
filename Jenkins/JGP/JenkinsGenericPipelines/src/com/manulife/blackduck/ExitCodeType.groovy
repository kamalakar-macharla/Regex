package com.manulife.blackduck

// https://github.com/blackducksoftware/synopsys-detect/blob/5.5.0/detect-configuration/src/main/java/com/synopsys/integration/detect/exitcode/ExitCodeType.java
enum ExitCodeType implements Serializable {
    UNEXPECTED(-1, 'BlackDuck unexpected error.'),
    SUCCESS(0, 'Project PASSED BlackDuck Open-Source Governance Gate!'),
    FAILURE_BLACKDUCK_CONNECTIVITY(1, 'Unable to connect to the BlackDuck portal.'),
    FAILURE_TIMEOUT(2, 'Unable to scan the project with BlackDuck within the defined timeframe.  Scan was aborted because of a timeout.'),
    FAILURE_POLICY_VIOLATION(3, 'Project FAILED BlackDuck Open-Source Governance Gate!'),
    FAILURE_PROXY_CONNECTIVITY(4, 'Failed connecting to a proxy.'),
    FAILURE_DETECTOR(5, 'The project\'s build tool failed to run in BlackDuck\'s bill-of-material discovery.'),
    FAILURE_SCAN(6, 'BlackDuck scan failure.'),
    FAILURE_CONFIGURATION(7, 'BlackDuck configuration failure.'),
    FAILURE_DIAGNOSTIC(8, 'BlackDuck diagnostic failure.'),
    FAILURE_DETECTOR_REQUIRED(9, 'BlackDuck missed bill-of-material tools.'),
    FAILURE_BLACKDUCK_VERSION_NOT_SUPPORTED(10, 'BlackDuck\'s \"signature scanner\" JAR appears outdated.'),
    FAILURE_BLACKDUCK_FEATURE_ERROR(11, 'BlackDuck saw an unexpected exception or failed to upload a file or failed to scan a binary file.'),
    FAILURE_POLARIS_CONNECTIVITY(12, 'BlackDuck failed to download its Polaris product.'),
    FAILURE_GENERAL_ERROR(99, 'BlackDuck general error.'),
    FAILURE_UNKNOWN_ERROR(100, 'BlackDuck unknown error.')

    private final int exitCode
    private final String descr

    ExitCodeType(final int exitCode, final String descr) {
        this.exitCode = exitCode
        this.descr = descr
    }

    static ExitCodeType lookup(int exitCode) {
        return (values().find { it.exitCode == exitCode } )
    }

    int getExitCode() {
        return exitCode
    }

    String getDescr() {
        return descr
    }
}
