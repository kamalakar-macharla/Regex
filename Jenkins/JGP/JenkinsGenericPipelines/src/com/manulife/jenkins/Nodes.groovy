package com.manulife.jenkins

/**
 *
 * This class contains knowledge about existing and valid node labels.
 *
 **/
class Nodes {
    private static final String[] LABELS = ['android_cicd_capable', 'concourse', 'ios_cicd_capable', 'linux', 'multi-platform-general', 'windows']

    static boolean isValidNodeLabel(String label) {
        return label in LABELS
    }

    static String getNodeLabels() {
        return LABELS.join(', ')
    }
}