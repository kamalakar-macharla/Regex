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
            stage('Read Parameters') {
                steps {
                     script {
                        FAILED_STAGE = env.STAGE_NAME
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        localBranchName = GitLabUtils.getLocalBranchName(this)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }
                    }
                }
            }
            stage('Deploy To Nifi')
            {
                steps {
                    script {
                        try {
                            FAILED_STAGE = env.STAGE_NAME
                            yamlFileName = pipelineParams.deployYAML + '.yml'
                            paramsFileName = pipelineParams.deployYAML + '.xlsx'

                            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nifi-db-cred',
                                usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                                    logger.debug("Map Vars 2 ${map_vars}")
                                    logger.debug("GIT COMMIT ${map_vars.GIT_COMMIT}")
                                    logger.debug("Build Number ${env.BUILD_NUMBER}")
                                    bat """ cd Nifi/bin
                                        pwd
                                        Admoveo.exe %USERNAME% %PASSWORD% $yamlFileName $localBranchName
                                        ls Nifi/Code/Outbound
                                        """
                                    sh 'rm -rf Nifi'
                            }
                        }
                        catch (err) {
                            logger.error("Error:${err}", err)
                            errMsg = 'Failure'
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
    propertiesCatalog.addOptionalProperty('smokeTestJenkinsJobName', 'Defaulting smokeTestJenkinsJobName property to null', null)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    return propertiesCatalog
}


