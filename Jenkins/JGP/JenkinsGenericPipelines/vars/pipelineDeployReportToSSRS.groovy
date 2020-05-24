import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.git.GitPropertiesCatalogBuilder
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
                            error("There are issues in the pipeline properties file content.  More information available in the Job's log.")
                        }

                        // This pipeline can only run on Windows nodes
                        if (isUnix() == true) {
                            error('This pipeline can only be executed on a Windows node.  Consider using "windows" label to identify the node that should be used to run this pipeline')
                        }
                    }
                }
            }
            stage ('Deploy Report') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        String[] reportNames = "${pipelineParams.reportNames}".split(',')
                        for (reportName in reportNames) {
                            bat """
                                set TargetURL=${pipelineParams.SSRSServerURL}
                                set TargetFolder=${pipelineParams.targetSSRSFolder}
                                set ReportFolder=${pipelineParams.sourceReportsFolder}
                                set ReportName=${reportName}
                                rs -i ${pipelineParams.deployScriptName} -s %TargetURL% -v ReportFolder=\"%ReportFolder%\" -v TargetFolder=\"%TargetFolder%\" -v ReportName=\"%ReportName%\"
                            """
                        }
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
                }
                cleanWs()
            }
        }
        parameters {
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
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

    propertiesCatalog.addMandatoryProperty('SSRSServerURL', 'URL of SSRS server such as http://mlipsgbvosql/reportserver')
    propertiesCatalog.addMandatoryProperty('targetSSRSFolder', 'Target folder for report in SSRS server')
    propertiesCatalog.addMandatoryProperty('sourceReportsFolder', 'Name of the folder that contains the reports to be deployed')
    propertiesCatalog.addMandatoryProperty('reportNames', 'Comma separated list of report names.  Do not include ".rdl" in the name(s)')
    propertiesCatalog.addOptionalProperty('deployScriptName', 'Name of the deployment script defined in Jenkins.  Defaults to "Deploy_Report.rss"', 'Deploy_Report.rss')

    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SSRS)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}