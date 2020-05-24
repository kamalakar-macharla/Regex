import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.codepromotion.CodePromotion
import com.manulife.gitlab.GitLabPromotionPropertiesCalalogBuilder
import com.manulife.logger.Logger
import com.manulife.pipeline.PipelineType
import com.manulife.releasenotes.ReleaseNotesGenerator
import com.manulife.report.ConfigurationReport
import com.manulife.report.ParametersReport
import com.manulife.report.ProductionSupportInfo
import com.manulife.report.SharedLibraryReport
import com.manulife.util.notifications.NotificationsPropertiesCalalogBuilder
import com.manulife.util.notifications.NotificationsSender
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertyFilesReader

/**
 * This pipeline is used to promote code from one branch to another and adjust
 * the project version number as necessary.
 *
 * The project versioning assumes there is a package.json file with a "version" property for the project.
 **/

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
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        com.manulife.logger.Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-promotion.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        releaseNotes = new ReleaseNotesGenerator(this)
                        codePromotion = new CodePromotion(this, PipelineType.NODEJS)
                    }
                }
            }
            stage('Prepare release notes') {
                when { expression { return pipelineParams.releaseNotesFlag == 'true' } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        releaseNotes.prepare()
                    }
                }
            }
            stage('Checkout source version') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        codePromotion.checkoutSourceVersion()
                    }
                }
            }
            stage('Update version in source branch') {
                when { expression { 'true' == pipelineParams.increaseFromBranchMinorVersion } }
                steps {
                    // If the configuration asks us to upgrade the source branch minor version, we do
                    //  it and then commit the updated package.json file to GitLab
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        codePromotion.updateVersionInSourceBranch()
                    }
                }
            }
            stage('Create new destination branch') {
                when { expression { return 'false' == pipelineParams.onlyOneReleaseBranch } }
                steps {
                    script {
                        codePromotion.updateVersionInDestinationBranch(true)
                    }
                }
            }
            stage('Promote in existing destination branch') {
                when { expression { return 'true' == pipelineParams.onlyOneReleaseBranch } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        codePromotion.updateVersionInDestinationBranch(false)
                    }
                }
            }
            stage ('Release notes generation') {
                when { expression { return pipelineParams.releaseNotesFlag == 'true' } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        cleanWs()
                        releaseNotes.generate()
                    }
                }
            }
            stage('Trigger Deployment and Automated Testing Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName } }
                steps {
                    //TODO - investigate if we want to pass a version # or commit_id on a later date
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                          wait: false
                }
            }
        }
        post {
            always {
                script {
                    def output = '***********************************************************************************\n'
                    for (reportLine in codePromotion.report) {
                        output += "${reportLine}\n"
                    }
                    output += '***********************************************************************************'
                    logger.info(output)

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
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            disableConcurrentBuilds()
            timeout(time: configuration.jenkinsJobTimeOutInMinutes, unit: 'MINUTES')
            timestamps()
        }
    }
}

def buildPropertiesCatalog() {
    def propertiesCatalog = new PropertiesCatalog()
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName', 'Defaulting deploymentJenkinsJobName property to null.', null)
    propertiesCatalog.addOptionalProperty('releaseNotesFlag', 'Flag for generating release notes', pipelineParams.releaseNotesFlag)

    GitLabPromotionPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.NODEJS)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
