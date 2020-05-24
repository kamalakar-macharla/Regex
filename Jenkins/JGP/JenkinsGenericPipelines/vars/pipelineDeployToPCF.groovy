import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.logger.Logger
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader


def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.agent}"
        }
        stages {
            stage('Init') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        logger.info("Received the following value for the commit_id parameter: ${params.commit_id}")
                    }
                }
            }
            stage ('Enterprise Pipeline Authentication') {
                environment {
                    CONCOURSE_CREDENTIALS = credentials("${pipelineParams.enterprisePCFCredentials}")
                    CONCOURSE_TEAM_URL = "${pipelineParams.concourseTeamURL}"
                    CONCOURSE_URL = "${pipelineParams.concourseURL}"
                    CONCOURSE_PIPELINE_JOB = "${pipelineParams.concourseJobName}"
                    PCF_SPACE = "${pipelineParams.enterprisePCFSpace}"
                    PCF_ENT_URL = "${pipelineParams.enterprisePCFUrl}"
                }
                options {
                    timeout(time: 10, unit: 'SECONDS')
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        CONCOURSE_TOKEN = sh (
                                    script: "uaa-auth-web ${CONCOURSE_CREDENTIALS_USR} ${CONCOURSE_CREDENTIALS_PSW}",
                                    returnStdout: true
                                ).trim()

                        logger.debug("CONCOURSE_TOKEN: ${CONCOURSE_TOKEN}")

                        if (CONCOURSE_TOKEN) {
                            logger.debug('Starting UAA Login...')
                            sh """fly -t ${CONCOURSE_PIPELINE_JOB} login --team-name ${PCF_SPACE} --concourse-url ${CONCOURSE_URL} --insecure << EOF
1
${CONCOURSE_TOKEN}
EOF"""

                        }
                        else {
                            error("The concourse bearer token was not generated based on the credentials provided for the enterprisePCFCredentials parameter = ${CONCOURSE_CREDENTIALS_USR}. " +
                                   'Please validate the credentials are correct and that Concourse is currently up')
                        }
                        logger.debug('End of UAA Login...')
                    }
                }
            }
            stage ('Deploy pipeline') {
                when { expression { return pipelineParams.concourseScriptPath } }
                environment {
                    CONCOURSE_SCRIPT_PATH = "${pipelineParams.concourseScriptPath}"
                    CONCOURSE_PIPELINE_NAME = "${pipelineParams.concoursePipelineName}"
                    CONCOURSE_PIPELINE_JOB = "${pipelineParams.concourseJobName}"
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        if (pipelineParams.secretPCFTokenCredentials != null) {
                            withCredentials([string(credentialsId: "${pipelineParams.secretPCFTokenCredentials}", variable: 'PCF_SECRET_TOKEN')]) {
                                logger.debug(PCF_SECRET_TOKEN)
                                sh """cd ${CONCOURSE_SCRIPT_PATH}
                                    fly -t ${CONCOURSE_PIPELINE_JOB} set-pipeline -c pipeline.yml -p ${CONCOURSE_PIPELINE_NAME} -n -l config.yml -v git_commit_id=${params.commit_id} -v secret_token=${PCF_SECRET_TOKEN}"""
                            }
                        }
                        else {
                            // TODO: There is a gap with this logic as the pipeline variable git_commit_id gets overridden each time this is run which is every environment _Deploy job.
                            //       The fix would be to only allow pipeline updates in the TST_Deploy pipeline when it passes the release promotion GIT COMMIT AUTOMATICALLY.
                            sh """cd ${CONCOURSE_SCRIPT_PATH}
                                fly -t ${CONCOURSE_PIPELINE_JOB} set-pipeline -c pipeline.yml -p ${CONCOURSE_PIPELINE_NAME} -n -l config.yml -v git_commit_id=${params.commit_id}"""
                        }
                    }
                }
            }
            stage ('Trigger pipeline') {
                environment {
                    CONCOURSE_PIPELINE_NAME = "${pipelineParams.concoursePipelineName}"
                    CONCOURSE_PIPELINE_JOB = "${pipelineParams.concourseJobName}"
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        sh """
                            fly -t ${CONCOURSE_PIPELINE_JOB} unpause-pipeline -p ${CONCOURSE_PIPELINE_NAME}
                            fly -t ${CONCOURSE_PIPELINE_JOB} trigger-job --job ${CONCOURSE_PIPELINE_NAME}/${CONCOURSE_PIPELINE_JOB} --watch
                        """
                    }
                }
            }
            stage ('Concourse Build Status') {
                environment {
                    CONCOURSE_PIPELINE_NAME = "${pipelineParams.concoursePipelineName}"
                    CONCOURSE_PIPELINE_JOB = "${pipelineParams.concourseJobName}"
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        sh """
                            fly -t ${CONCOURSE_PIPELINE_JOB} jobs -p ${CONCOURSE_PIPELINE_NAME}
                        """
                    }
                }
            }
            stage('Trigger Automated Smoke Test Pipeline') {
                when { expression { return pipelineParams.smokeTestJenkinsJobName } }
                steps {
                    build job: "${pipelineParams.smokeTestJenkinsJobName}",
                          wait: false
                }
            }
        }
        post {
            always {
                script {
                    sh "fly -t ${pipelineParams.concourseJobName} logout"
                    // Assume that the following error message
                    //  sh: line 1: 61489 Terminated: 15          sleep 3
                    // indicates that the above fly command spawns a daemon
                    // waiting for the response.  Then give it more time to
                    // wait.
                    sleep(5)
                    cleanWs()
                    PipelineRunAuditTrailing.log(this, "${pipelineParams?.concourseJobName}")
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()
                }
            }
        }
        parameters {
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
            string(
                name: 'commit_id',
                defaultValue: 'latest',
                description: 'Git Commit hash to deploy.'
            )
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()
    propertiesCatalog.addOptionalProperty('smokeTestJenkinsJobName', 'Defaulting smokeTestJenkinsJobName property to null', null)
    propertiesCatalog.addMandatoryProperty('enterprisePCFCredentials',
                                           'Missing the Jenkins username/password credential id of a Concourse user for authentication.' +
                                            'Make sure that the user has PCF DEV developer space access Example: PCF_DEV_CREDENTIALS')
    propertiesCatalog.addMandatoryProperty('enterprisePCFSpace', 'Missing enterprisePCFSpace the concourse team name assigned without environment trailing.  Example: CDN-EXAMPLE')
    propertiesCatalog.addOptionalProperty('enterprisePCFUrl',
                                          'Defaulting the Enterprise PCF URL to https://login.sys.cac.preview.pcf.manulife.com/oauth/token',
                                          'https://login.sys.cac.preview.pcf.manulife.com/oauth/token')
    propertiesCatalog.addMandatoryProperty('concoursePipelineName', 'Missing the concourse pipeline name you would like to run.  Example: pipeline-name')
    propertiesCatalog.addMandatoryProperty('concourseJobName', 'Missing the concourse job name you would like to run.  Example: deploy-job-name-env')
    propertiesCatalog.addOptionalProperty('concourseTeamURL',
                                          'Defaulting ConcourseURL property to https://concourse.platform.manulife.io/sky/token?team_name=',
                                          'https://concourse.platform.manulife.io/sky/token?team_name=')
    propertiesCatalog.addOptionalProperty('concourseURL', 'Defaulting ConcourseURL property to https://concourse.platform.manulife.io', 'https://concourse.platform.manulife.io')
    propertiesCatalog.addOptionalProperty('concourseScriptPath', 'Defaulting concourseScriptPath property to concourse_pipeline. Example: concourse_pipeline', null)
    propertiesCatalog.addOptionalProperty('secretPCFTokenCredentials', 'Defaulting secretPCFTokenCredentials to null', null)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}
