package com.manulife.git

import com.cloudbees.groovy.cps.NonCPS

enum GitFlowType implements Serializable {
    GITFLOW(0, 'GitFlow', true, true, true),
//  We will eventually add support for trunk based development...
//    TRUNKBASED(1, 'Trunk based development', false, false, true),

    final int code
    final String description
    final boolean hasDevelopBranch
    final boolean hasReleaseBranch
    final boolean hasMasterBranch

    GitFlowType(int code,
                String description,
                boolean hasDevelopBranch,
                boolean hasReleaseBranch,
                boolean hasMasterBranch) {
        this.code = code
        this.description = description
        this.hasDevelopBranch = hasDevelopBranch
        this.hasReleaseBranch = hasReleaseBranch
        this.hasMasterBranch  = hasMasterBranch
    }

    static GitFlowType lookup(int code) {
        return (values().find { it.exitCode == code } )
    }

    static GitFlowType lookup(String name) {
        return (values().find { it.name == name } )
    }

    @NonCPS
    static GitFlowType customValueOf(String gitFlowTypeName) {
        GitFlowType[] gitFlowTypes = GitFlowType.values()

        for (GitFlowType gitFlowType : gitFlowTypes) {
            if (gitFlowType.name() == gitFlowTypeName) {
                return gitFlowType
            }
        }

        throw new IllegalArgumentException('No GitFlowType enum entry defined for this string: ' + gitFlowTypeName)
    }
}