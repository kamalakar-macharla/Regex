import com.manulife.audittrail.StagesExecutionTimeTracker
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.gitlab.GitLabUtils
import com.manulife.logger.Logger
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader

// TODO:
//  - Add Support for Docker Container

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        stages {
            stage('Init') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker = new StagesExecutionTimeTracker()
                        stagesExecutionTimeTracker.initStageStart()

                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()
                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)
                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-tst.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error("There are issues in the pipeline properties file content. More information available in the Job's log.")
                        }

                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Execute DevTest Test Suite') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        stagesExecutionTimeTracker.runUnitTestsStageStart()
                        //Check for appropriate variables for devtest execution
                        //Check for test suite mar param
                        marFile = ''
                        if (params.MarFile) {
                            marFile = "${params.MarFile}"
                        }

                        //Use the DevTest plugin to execute test cases from MAR file
                        svDeployTest testType: 'suites', marFilePath: "MarFiles/${marFile}"
                        stagesExecutionTimeTracker.runUnitTestsStageEnd()

                    }
                }
            }
            stage('Generate Test Reports') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        //Use the DevTest plugin to publish the test results to Jenkins
                        svPublishTestReport()
                    }
                }
            }
        }
        post {
            always {
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()

                    cleanWs()
                }
            }
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '1'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
        triggers {
            gitlab(
                triggerOnPush: false,
                triggerOnMergeRequest: false,
                triggerOpenMergeRequestOnPush: 'never',
                triggerOnNoteRequest: true,
                noteRegex: 'Jenkins please retry a build',
                skipWorkInProgressMergeRequest: true,
                ciSkip: true,
                setBuildDescription: true,
                addNoteOnMergeRequest: true,
                addCiMessage: true,
                addVoteOnMergeRequest: true,
                acceptMergeRequestOnSuccess: false,
                branchFilterType: 'RegexBasedFilter',
                targetBranchRegex: '',
                secretToken: '')
        }
        parameters {
            choice(
                name:'MarFile',
                choices: "${configuration.marFile}",
                description: 'Select which test suite you would like to run.')
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console')
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.DEVTEST)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}
