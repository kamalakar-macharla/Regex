package com.manulife.util

/**
 *
 *
 *
 **/
class Conditions {
    // The default expression where the exclamation mark denotes a meta-regex negation.
    public static final String DEFAULT_TOOL_TRIGGERS = '! dev|devel|develop|dev/.*|feature/.*|fix/.*|hotfix/.*|patch-.*'

    /**
     * Matches the build cause and the local branch name against a tool applicability specifier such as
     *      fortifyTriggers: (release|prod)/.*
     *      hubTriggers:     (release|prod)/.*
     */
    static boolean isToolAllowed(Script scriptObj, String toolName, String triggers, String localBranchName) {
        scriptObj.logger.debug("${toolName}Triggers: ${triggers}")
        boolean toolAllowed

        if (scriptObj.env.gitlabActionType == 'MERGE' || scriptObj.env.gitlabActionType == 'NOTE') {
            scriptObj.logger.debug("${toolName} allowed? false")
            return false
        }

        def theTriggers = (triggers ?: DEFAULT_TOOL_TRIGGERS).trim()
        def invert = false
        if (theTriggers.startsWith('!')) {
            invert = true
            theTriggers = theTriggers[1..-1].trim()
        }
        toolAllowed = (localBranchName ==~ theTriggers)
        if (invert) {
            toolAllowed = !toolAllowed
        }

        scriptObj.logger.debug("${toolName} allowed? ${toolAllowed}")
        return toolAllowed
    }
}

