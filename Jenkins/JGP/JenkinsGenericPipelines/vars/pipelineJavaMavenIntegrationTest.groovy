import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.logger.Logger
import com.manulife.maven.MavenPropertiesCalalogBuilder
import com.manulife.pipeline.PipelineType
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.htmlpublisher.HtmlPublisher
import com.manulife.util.htmlpublisher.HtmlPublisherPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader

// TODO:
//  - Some teams may want to package in a Docker container or installation package.  How do we support that?

def call(Map configuration) {

    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        tools {
            maven 'Maven 3.3.9'
            jdk 'JDK 8u112'
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
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-test.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        // Maven
                        mvnSettings = "-B -U -s ${WORKSPACE}/settings.xml -f " + pipelineParams.mavenPOMRelativeLocation

                        logger.debug("WORKSPACE = ${WORKSPACE}")

                        configFileProvider([configFile(fileId: pipelineParams.mavenSettingsFileName, targetLocation: 'settings.xml')]) { }

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
            stage('Execute Tests') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        if (unix) {
                            sh "mvn ${mvnSettings} ${pipelineParams.mavenIntegrationTestGoal}"
                        }
                        else {
                            bat "mvn ${mvnSettings} ${pipelineParams.mavenIntegrationTestGoal}"
                        }
                    }
                }
            }
            stage('Trigger Next Pipeline') {
                when { expression { return pipelineParams.promotionJenkinsJobName } }
                steps {
                    build job: "${pipelineParams.promotionJenkinsJobName}",
                          wait: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
                }
            }
        }
        post {
            always {
                script {
                    // Send notifications that may include the above console log with gating messages.
                    PipelineRunAuditTrailing.log(this)
                    new NotificationsSender(this, pipelineParams).send()
                    new HtmlPublisher(this, pipelineParams).publish()
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

    MavenPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.JAVA_MAVEN_TEST)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)
    HtmlPublisherPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
