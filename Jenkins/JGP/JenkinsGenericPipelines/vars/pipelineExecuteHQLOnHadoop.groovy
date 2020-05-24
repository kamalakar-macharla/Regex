import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.logger.Logger
import com.manulife.pipeline.PipelineDependencies
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader


/**
 * This pipelines makes it possible to execute HQLs on Hadoop.
 *
 * It performs the following steps:
 *   - Creates a temporary folder on the Edge node
 *   - Copies the HQL files to that temporary folder
 *   - Executes the HQL
 *   - Deletes the temporary folder on the Edge node
 *
 * The job is executed according to a cron schedule that can be specified in the .Jenkinsfile as the cronExpression property.
 **/
def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.agent}"
        }
        environment {
            PATH = "/usr/local/bin:$PATH"
        }
        stages {
            stage ('Read Parameters') {
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
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-execute.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }
                    }
                }
            }
            stage ('Check status of upstream jobs') {
                when { expression { return pipelineParams.upstreamJobNames } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def errorMsg = PipelineDependencies.checkDependencies(this, pipelineParams.upstreamJobNames)
                        if (errorMsg) {
                            error("This pipeline can't be executed because of upstream jobs dependencies: ${errorMsg}")
                        }
                    }
                }
            }
            stage ('Fix files format') {
                steps {
                    // TODO:  For now, it only converts the files in the 1st level subfolders of the workspace.  We may need something recursive at some point.
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def unix = isUnix()
                        if (!unix) {  // Windows...
                            // Convert files in all subfolders except jenkins/
                            powershell returnStdout: true,  script: '''$files = Get-ChildItem -File -recurse -Exclude *.Jenkinsfile,*.properties
                                            for ($i=0; $i -lt $files.Count; $i++) {
                                                if($files[$i].Name -ne "jenkins") {
                                                    Write-Host "Converting file: $($files[$i].Name)"
                                                    dos2unix $files[$i].FullName
                                                }
                                            }'''
                        }
                        logger.debug('Fixed the CRLF from Windows to Linux format.')
                    }
                }
            }
            stage ('Cleanup on Edge Node') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        sh 'ps -p $$'

                        logger.debug("Deleting temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")

                        try {
                            sh getSSHCommand(pipelineParams) + " \"${pipelineParams.context_commands} rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\""
                        }
                        catch (e) {
                            logger.error("Unable to run: rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\".  Got this exception: ${e.message}", e)
                        }

                        try {
                            sh getSSHCommand(pipelineParams) + " \"${pipelineParams.context_commands} rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}\""
                        }
                        catch (e) {
                            logger.error("Unable to run: rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\".  Got this exception: ${e.message}", e)
                        }
                    }
                }
            }
            stage ('Copy HQL files on Edge Node') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug("Creating temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")
                        sh "${getSSHCommand(pipelineParams)} \"${pipelineParams.context_commands} mkdir ${pipelineParams.home_folder}/${JOB_BASE_NAME}\""

                        logger.debug('Copying HQL files to edge node')
                        sh "scp -r ${getSSHOptions(pipelineParams)} ${pipelineParams.hql_files_folder}/* " +
                            "${pipelineParams.user_name}@${pipelineParams.edge_node}:${pipelineParams.home_folder}/${JOB_BASE_NAME}"
                    }
                }
            }
            stage('Run HQL on Hadoop') {
                steps {
                    FAILED_STAGE = env.STAGE_NAME
                    sh "${getSSHCommand(pipelineParams)} \"${pipelineParams.context_commands} cd ${pipelineParams.home_folder}/${JOB_BASE_NAME} && ${pipelineParams.run_HQL_command}\""
                }
            }
            stage('Trigger Child Pipeline') {
                when { expression { return pipelineParams.childJenkinsJobName } }
                steps {
                    FAILED_STAGE = env.STAGE_NAME
                    build job: "${pipelineParams.childJenkinsJobName}",
                          wait: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                }
            }
        }
        post {
            always {
                script {
                    try {
                        unix = isUnix()
                        if (unix) {
                            sh "mkdir ${pipelineParams.logs_folder_name}"
                        }
                        else {
                            bat "md ${pipelineParams.logs_folder_name}"
                        }

                        logger.debug('Copying log files from edge node to Jenkins workspace')
                        sh "scp -r ${getSSHOptions(pipelineParams)} ${pipelineParams.user_name}@${pipelineParams.edge_node}:${pipelineParams.home_folder}/${JOB_BASE_NAME}/logs/*.log " +
                            "${pipelineParams.logs_folder_name} "
                    }
                    catch (e) {
                        logger.error("Unable to copy back the log files from Edge node to Jenkins. Got this exception: ${e.message}", e)
                    }
                }

                archiveArtifacts allowEmptyArchive: true, artifacts: "${pipelineParams.logs_folder_name}/**/*.*", caseSensitive: false, defaultExcludes: false

                script {
                    if (params.debug_mode == false) {
                        logger.debug("Deleting temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")

                        try {
                            sh getSSHCommand(pipelineParams) + " \"${pipelineParams.context_commands} rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\""
                        }
                        catch (e) {
                            logger.error("Unable to run: rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\".  Got this exception: ${e.message}", e)
                        }

                        try {
                            sh getSSHCommand(pipelineParams) + " \"${pipelineParams.context_commands} rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}\""
                        }
                        catch (e) {
                            logger.error("Unable to run: rm -rvf ${pipelineParams.home_folder}/${JOB_BASE_NAME}/*\".  Got this exception: ${e.message}", e)
                        }
                    }
                    else {
                        logger.warning("Pipeline executed in Debug mode.  Not deleting the temporatory folder: ${pipelineParams.home_folder}/${JOB_BASE_NAME}")
                    }
                }

                // There is currently a bug with the GitLab plugin.  We need to give him some time to release its handles on the files in the workspace or the cleanWs() method will fail.
                sleep time:10, unit: 'SECONDS'
                script {
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()

                    try {
                        cleanWs()
                    }
                    catch (e) {
                        logger.warning('Unable to cleanup workspace')
                    }
                }
            }
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
        triggers {
            cron(configuration.cronExpression)
        }
        parameters {
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
            booleanParam(
                name: 'debug_mode',
                defaultValue: false,
                description: 'Allows execution of the pipeline in debug mode which will not delete the temporary folder on the edge node'
            )
        }
    }
}

def getSSHCommand(Properties pipelineParams) {
    return "ssh ${getSSHOptions(pipelineParams)} ${pipelineParams.user_name}@${pipelineParams.edge_node} "
}

def getSSHOptions(Properties pipelineParams) {
    return "-v -o ServerAliveInterval=60 -i ${pipelineParams.identity_file} -o StrictHostKeyChecking=no"
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addMandatoryProperty('edge_node', 'Missing the edge node name.')
    propertiesCatalog.addMandatoryProperty('identity_file', 'Missing the location (path + name) of the SSH identity file for the user to be used on the edge node.')
    propertiesCatalog.addMandatoryProperty('user_name', 'Missing the name of the user to be used on the edge node.')
    propertiesCatalog.addMandatoryProperty('home_folder', 'Missing the home folder of the user on the edge node.')
    propertiesCatalog.addMandatoryProperty('run_HQL_command', 'Missing the command used to run/execute the HQL files on Hadoop.')
    propertiesCatalog.addMandatoryProperty('hql_files_folder', 'Missing the name of the folder (in the workspace/GitLab project) that contains the HQL files to be executed.')

    propertiesCatalog.addOptionalProperty('context_commands',
                                          'Linux commands that provide some context about the location where the actions are taken.  ' +
                                            'Defaults to \"hostname && pwd &&\".  Could be assigned an empty string is not desired.',
                                          'hostname && pwd && ')
    propertiesCatalog.addOptionalProperty('logs_folder_name',
                                          'Defaulting logs_folder_name property to \"logs\". Folder where the script executed on Edge node will copy back the execution log files.  ' +
                                            'That folder is archived by Jenkins at the end of the pipeline.',
                                          'logs')
    propertiesCatalog.addOptionalProperty('childJenkinsJobName',
                                          'Defaulting childJenkinsJobName to null.  ' +
                                            'Could be set to the path/Name of a Jenkins job that should be triggered after a successful execution of this pipelines.',
                                          null)
    propertiesCatalog.addOptionalProperty('upstreamJobNames',
                                          'Defaulting upstreamJobNames to null.  ' +
                                            'Could be set to the path/Name of a Jenkins job that must have been executed successfuly before the execution of this pipelines.  ' +
                                            'An example would be [{Name: IMIT_Projects/IMIT_BigData/IMIT_Marketing_Automation/IMIT_BigData_MA_MMT_Execute_EVENT_HQL_PRD}, ' +
                                            '{Name: IMIT_Projects/IMIT_BigData/IMIT_Marketing_Automation/IMIT_BigData_MA_MMT_Execute_MAIN_HQL_PRD}]',
                                          null)

    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
