package com.manulife.jenkins

import com.manulife.util.Strings

/**
 *
 * Represents the current Jenkins job name.
 *
 **/
class JobName implements Serializable {
    // Project names are in this format: <Segment>_<BusinessUnit>_<ProjectName>_<BranchName>_<CI|Deploy|Test|...>
    private static final SEGMENT_INDEX = 0
    private static final BUSINESS_UNIT_INDEX = 1

    private final Script scriptObj
    private final String fullJobName

    JobName(Script scriptObj) {
        this.scriptObj = scriptObj

        String[] tokens = "${scriptObj.JOB_NAME}".tokenize('/')
        int tokensLength = tokens.length

        // For most branches, the job name is the last element of the JOB_NAME environment variable.
        // But for multi-branch pipelines, the job name is the 2nd part from the end, not the last one.
        // Example:  JOB_NAME=Example_Projects/Example_1_2/Example_Java/Example_Java_Microservice_PCF_Feature_CI/feature%2Ftest444
        String nameEnd = tokens[tokensLength - 1]
        if (nameEnd.startsWith('feature') || nameEnd.startsWith('fix') || nameEnd.startsWith('hotfix')) {
            nameEnd = tokens[tokensLength - 2]
        }

        if (nameEnd.matches('(AP_|APAC_|ETS_|GF_|GSD_|GSPE_|JH_|MFC_).*')) {
            this.fullJobName = nameEnd
        }
        else {
            // We currently don't prefix the CDT Jenkins job names with MFC_ ...
            // TODO: When we also prefix CDT jobs with MFC_ in Jenkins we should turn this if statement into a validation instead (throw exception is unrecognized Segment)
            this.fullJobName = 'MFC_' + nameEnd
        }

        if (this.fullJobName.split('_').size() < 5) {
            throw new JenkinsException('''The project name doesn't follow the naming convention: <Segment>_<BusinessUnit>_<ProjectName>_<BranchName>_<CI|Deploy|Test|...>''')
        }
    }

    // The job name will be similar to: "MFC_Example_MyProject_DEV_CI"
    String getJobName() {
        return this.fullJobName
    }

    // The job name will be similar to: "MFC_Example_MyProject"
    String getProjectName() {
        return Strings.canonicalizeAppKey(this.fullJobName)
    }

    String getSegmentName() {
        String[] tokens = this.jobName.split('_')
        return tokens[SEGMENT_INDEX]
    }

    String getBUName() {
        String[] tokens = this.jobName.split('_')
        return tokens[BUSINESS_UNIT_INDEX]
    }
}
