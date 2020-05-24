import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.gitlab.GitLabUtils
import com.manulife.logger.Logger
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader
import com.manulife.util.htmlpublisher.HtmlPublisher
import com.manulife.util.htmlpublisher.HtmlPublisherPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.agent}"
        }
        tools {
            jdk 'JDK 8u112'
        }
        stages {
            stage('Init') {
                steps {
                    script {
                        // Print the banner !!!
                        FAILED_STAGE = env.STAGE_NAME
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        // Assign local branch name var
                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-test.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        unix = isUnix()

                        logger.debug('Environment Variables:')
                            {
                                if (unix) {
                                    sh 'env'
                                }
                                else {
                                    bat 'set'
                                }
                            }
                    }
                }
            }
            stage('Execute Integration Test') {
                when { expression { return pipelineParams.nodeIntegrationTestCommand } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        if (unix) {
                            sh "${pipelineParams.nodeBuildCommand}"
                            sh "${pipelineParams.nodeIntegrationTestCommand}"
                        }
                        else {
                            bat "${pipelineParams.nodeBuildCommand}"
                            bat "${pipelineParams.nodeIntegrationTestCommand}"
                        }
                    }
                }
            }
            stage('Trigger Next Pipeline') {
                when { expression { return pipelineParams.promotionJenkinsJobName && ('MERGE' != env.gitlabActionType) } }
                steps {
                    build job: "${pipelineParams.promotionJenkinsJobName}",
                          wait: false
                }
            }
        }
        post {
            always {
                script {
                    if (pipelineParams.nodeTestReportGenerateCommand != null) {
                        if (unix) {
                            sh "${pipelineParams.nodeTestReportGenerateCommand}"
                        }
                        else {
                            bat "${pipelineParams.nodeTestReportGenerateCommand}"
                        }
                    }

                    // Send notifications that may include the above console log with gating messages.
                    PipelineRunAuditTrailing.log(this)
                    new HtmlPublisher(this, pipelineParams).publish()
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
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()

    propertiesCatalog.addOptionalProperty('promotionJenkinsJobName',
                                          'Defaulting promotionJenkinsJobName to null.  Could be set to the path/Name of the Jenkins job to be triggered after the execution of this pipelines.',
                                          null)
    propertiesCatalog.addOptionalProperty('nodeIntegrationReportName', 'Defaulting nodeIntegrationReportName to null.  Could be any name you want.', null)
    propertiesCatalog.addOptionalProperty('nodeIntegrationReportHtmlFile', 'Defaulting nodeIntegrationReportHtmlFile to null.  Could be index.htnl for more framework.', null)
    propertiesCatalog.addOptionalProperty('nodeIntegrationReportRelativePath', 'Defaulting nodeIntegrationReportRelativePath to null.  Could be serenity/report for serenity framework.', null)
    propertiesCatalog.addOptionalProperty('nodeIntegrationTestCommand', 'Defaulting nodeIntegrationTestCommand to null.  Could be set to npm run test:integration.', null)
    propertiesCatalog.addOptionalProperty('nodeBuildCommand', 'Defaulting nodeBuildCommand to null.  Could be set to npm install.', null)
    propertiesCatalog.addOptionalProperty('nodeTestReportGenerateCommand', 'Defaulting nodeTestReportGenerateCommand to null.  Could be set to npm run test:generate report.', null)

    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    HtmlPublisherPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
