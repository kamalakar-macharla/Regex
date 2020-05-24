package com.manulife.audittrail

/**
  *
  * This class represents one pipeline execution audit trail entry
  *
  */
class StagesExecutionTimeTracker implements Serializable {
    long initStageStartInMillis
    long initStageEndInMillis
    long increasePatchVersionStageStartInMillis
    long increasePatchVersionStageEndInMillis
    long resolveDependenciesAndBuildStageStartInMillis
    long resolveDependenciesAndBuildStageEndInMillis
    long runUnitTestsStageStartInMillis
    long runUnitTestsStageEndInMillis
    long codeReviewStageStartInMillis
    long codeReviewStageEndInMillis
    long openSourceGovernanceStageStartInMillis
    long openSourceGovernanceStageEndInMillis
    long securityCodeScanningStageStartInMillis
    long securityCodeScanningStageEndInMillis
    long packageAndStoreStageStartInMillis
    long packageAndStoreStageEndInMillis
    long downloadBinaryStageStartInMillis
    long downloadBinaryStageEndInMillis
    long prepareRequestStageStartInMillis
    long prepareRequestStageEndInMillis
    long manageServiceDependenciesStageStartInMillis
    long manageServiceDependenciesStageEndInMillis
    long submitRequestApiStageStartInMillis
    long submitRequestApiStageEndInMillis
    long monitorProgressStageStartInMillis
    long monitorProgressStageEndInMillis
    long logBinaryStatusStageStartInMillis
    long logBinaryStatusStageEndInMillis

    private static final UNDEFINED = 0

    void initStageStart() {
        initStageStartInMillis = System.currentTimeMillis()
    }

    void initStageEnd() {
        initStageEndInMillis = System.currentTimeMillis()
    }

    def initStageDuration() {
        if (initStageStartInMillis > 0 && initStageEndInMillis > 0) {
            return initStageEndInMillis - initStageStartInMillis
        }
        return UNDEFINED
    }

    void increasePatchVersionStageStart() {
        increasePatchVersionStageStartInMillis = System.currentTimeMillis()
    }

    void increasePatchVersionStageEnd() {
        increasePatchVersionStageEndInMillis = System.currentTimeMillis()
    }

    def increasePatchVersionStageDuration() {
        if (increasePatchVersionStageStartInMillis > 0 && increasePatchVersionStageEndInMillis > 0) {
            return increasePatchVersionStageEndInMillis - increasePatchVersionStageStartInMillis
        }
        return UNDEFINED
    }

    void resolveDependenciesAndBuildStageStart() {
        resolveDependenciesAndBuildStageStartInMillis = System.currentTimeMillis()
    }

    void resolveDependenciesAndBuildStageEnd() {
        resolveDependenciesAndBuildStageEndInMillis = System.currentTimeMillis()
    }

    def resolveDependenciesAndBuildStageDuration() {
        if (resolveDependenciesAndBuildStageStartInMillis > 0 && resolveDependenciesAndBuildStageEndInMillis > 0) {
            return resolveDependenciesAndBuildStageEndInMillis - resolveDependenciesAndBuildStageStartInMillis
        }
        return UNDEFINED
    }

    void runUnitTestsStageStart() {
        runUnitTestsStageStartInMillis = System.currentTimeMillis()
    }

    void runUnitTestsStageEnd() {
        runUnitTestsStageEndInMillis = System.currentTimeMillis()
    }

    def runUnitTestsStageDuration() {
        if (runUnitTestsStageStartInMillis > 0 && runUnitTestsStageEndInMillis > 0) {
            return runUnitTestsStageEndInMillis - runUnitTestsStageStartInMillis
        }
        return UNDEFINED
    }

    void codeReviewStageStart() {
        codeReviewStageStartInMillis = System.currentTimeMillis()
    }

    void codeReviewStageEnd() {
        codeReviewStageEndInMillis = System.currentTimeMillis()
    }

    def codeReviewStageDuration() {
        if (codeReviewStageStartInMillis > 0 && codeReviewStageEndInMillis > 0) {
            return codeReviewStageEndInMillis - codeReviewStageStartInMillis
        }
        return UNDEFINED
    }

    void openSourceGovernanceStageStart() {
        openSourceGovernanceStageStartInMillis = System.currentTimeMillis()
    }

    void openSourceGovernanceStageEnd() {
        openSourceGovernanceStageEndInMillis = System.currentTimeMillis()
    }

    def openSourceGovernanceStageDuration() {
        if (openSourceGovernanceStageStartInMillis > 0 && openSourceGovernanceStageEndInMillis > 0) {
            return openSourceGovernanceStageEndInMillis - openSourceGovernanceStageStartInMillis
        }
        return UNDEFINED
    }

    void securityCodeScanningStageStart() {
        securityCodeScanningStageStartInMillis = System.currentTimeMillis()
    }

    void securityCodeScanningStageEnd() {
        securityCodeScanningStageEndInMillis = System.currentTimeMillis()
    }

    def securityCodeScanningStageDuration() {
        if (securityCodeScanningStageStartInMillis > 0 && securityCodeScanningStageEndInMillis > 0) {
            return securityCodeScanningStageEndInMillis - securityCodeScanningStageStartInMillis
        }
        return UNDEFINED
    }

    void packageAndStoreStageStart() {
        packageAndStoreStageStartInMillis = System.currentTimeMillis()
    }

    void packageAndStoreStageEnd() {
        packageAndStoreStageEndInMillis = System.currentTimeMillis()
    }

    def packageAndStoreStageDuration() {
        if (packageAndStoreStageStartInMillis > 0 && packageAndStoreStageEndInMillis > 0) {
            return packageAndStoreStageEndInMillis - packageAndStoreStageStartInMillis
        }
        return UNDEFINED
    }

    void downloadBinaryStageStart() {
        downloadBinaryStageStartInMillis = System.currentTimeMillis()
    }

    void downloadBinaryStageEnd() {
        downloadBinaryStageEndInMillis = System.currentTimeMillis()
    }

    def downloadBinaryStageDuration() {
        if (downloadBinaryStageStartInMillis > 0 && downloadBinaryStageEndInMillis > 0) {
            return downloadBinaryStageEndInMillis - downloadBinaryStageStartInMillis
        }
        return UNDEFINED
    }

    void prepareRequestStageStart() {
        prepareRequestStageStartInMillis = System.currentTimeMillis()
    }

    void prepareRequestStageEnd() {
        prepareRequestStageEndInMillis = System.currentTimeMillis()
    }

    def prepareRequestStageDuration() {
        if (prepareRequestStageStartInMillis > 0 && prepareRequestStageEndInMillis > 0) {
            return prepareRequestStageEndInMillis - prepareRequestStageStartInMillis
        }
        return UNDEFINED
    }

    void manageServiceDependenciesStageStart() {
        manageServiceDependenciesStageStartInMillis = System.currentTimeMillis()
    }

    void manageServiceDependenciesStageEnd() {
        manageServiceDependenciesStageEndInMillis = System.currentTimeMillis()
    }

    def manageServiceDependenciesStageDuration() {
        if (manageServiceDependenciesStageStartInMillis > 0 && manageServiceDependenciesStageEndInMillis > 0) {
            return manageServiceDependenciesStageEndInMillis - manageServiceDependenciesStageStartInMillis
        }
        return UNDEFINED
    }

    void submitRequestApiStageStart() {
        submitRequestApiStageStartInMillis = System.currentTimeMillis()
    }

    void submitRequestApiStageEnd() {
        submitRequestApiStageEndInMillis = System.currentTimeMillis()
    }

    def submitRequestApiStageDuration() {
         if (submitRequestApiStageStartInMillis > 0 && submitRequestApiStageEndInMillis > 0) {
            return submitRequestApiStageEndInMillis - submitRequestApiStageStartInMillis
        }
        return UNDEFINED
    }

    void monitorProgressStageStart() {
        monitorProgressStageStartInMillis = System.currentTimeMillis()
    }

    void monitorProgressStageEnd() {
        monitorProgressStageEndInMillis = System.currentTimeMillis()
    }

    def monitorProgressStageDuration() {
        if (monitorProgressStageStartInMillis > 0 && monitorProgressStageEndInMillis > 0) {
            return monitorProgressStageEndInMillis - monitorProgressStageStartInMillis
        }
        return UNDEFINED
    }

    void logBinaryStatusStageStart() {
        logBinaryStatusStageStartInMillis = System.currentTimeMillis()
    }

    void logBinaryStatusStageEnd() {
        logBinaryStatusStageEndInMillis = System.currentTimeMillis()
    }

    def logBinaryStatusStageDuration() {
        if (logBinaryStatusStageStartInMillis > 0 && logBinaryStatusStageEndInMillis > 0) {
            return logBinaryStatusStageEndInMillis - logBinaryStatusStageStartInMillis
        }
        return UNDEFINED
    }
}
