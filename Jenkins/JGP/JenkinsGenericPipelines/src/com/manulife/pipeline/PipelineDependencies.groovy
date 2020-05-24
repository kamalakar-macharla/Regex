package com.manulife.pipeline

import jenkins.model.*
import hudson.model.*
import com.cloudbees.hudson.plugins.folder.*
import jenkins.branch.*
import org.jenkinsci.plugins.workflow.job.*
import org.jenkinsci.plugins.workflow.multibranch.*
import groovy.json.*

/**
 *
 * This class knows how to verify if some pipelines were executed successfully.
 * It can be used to verify that a pipeline's dependencies were executed if they are a pre-requisite for another pipeline to run.
 *
 **/
class PipelineDependencies {
    static String checkDependencies(def scriptObj, String upStreamJobNames) {
        // Example: upStreamJobNames = '{"jobs":[{"Name": "IMIT_Projects/IMIT_BigData/IMIT_Ingestion/IMIT_BigData_Ingestion_MC_PRD"}]}'
        def result = new JsonSlurper().parseText(upStreamJobNames)
        if (result.jobs.size() == 0) {
            scriptObj.logger.info('No upstream jobs.')
            return null
        }

        scriptObj.logger.debug("result  = ${result}")

        def parentJobNames = []
        for (int i = 0; i < result.jobs.size(); i++) {
            parentJobNames.add(result.jobs[i].Name)
        }

        def jobsMap = [:]
        for (item in Jenkins.instance.items) {
            findJobItems(item, jobsMap)
        }

        def errorMsg = null
        for (parentJobName in parentJobNames) {
            def jobItem = jobsMap[parentJobName]

            if (jobItem == null) {
                errorMsg = 'Unable to find upstream job called: ' + parentJobName
                break
            }

            def lastBuild = jobItem.lastBuild

            scriptObj.logger.info("upstream job ${parentJobName} found with lastBuild result = ${lastBuild.result}")

            if (lastBuild.result != Result.SUCCESS && lastBuild.result != Result.UNSTABLE) {
                errorMsg = 'The last execution of the following upstream job was unsuccessful: ' + parentJobName
                break
            }

            // def upstreamExecutionDate = lastBuild.getTime().format( 'EEE MMM dd' )  // Mon Oct 01
            // scriptObj.echo "upstream job last execution was on ${upstreamExecutionDate}"

            // def currentDate = new Date().format( 'EEE MMM dd' )  // Mon Oct 01
            // scriptObj.echo "current date is ${currentDate}"

            // if(!upstreamExecutionDate.startsWith(currentDate)) {
            //     errorMsg = 'The last execution of the following upstream job wasn\'t today: ' + parentJobName
            //     break
            // }
        }

        return errorMsg
    }

    private static void findJobItems(item, jobsMap) {
        if (item instanceof WorkflowJob) {
            jobsMap[item.fullname] = item
        }
        else if (item instanceof Folder) {
            for (subItem in item.items) {
                findJobItems(subItem, jobsMap)
            }
        }
        else if (item instanceof OrganizationFolder) {
            for (subItem in item.items) {
                findJobItems(subItem, jobsMap)
            }
        }
    }
}