package com.manulife.pipeline

import com.manulife.jenkins.JobName

/**
 *
 * Collection of pipelines utilities.
 *
 **/
class PipelineUtils {
    static String buildCause(gitlabActionType) {
        return gitlabActionType ?: 'JENKINS_MANUAL'
    }

    static String getJobName(scriptObj) {
        JobName jobName = new JobName(scriptObj)
        return jobName.getJobName()
    }
}