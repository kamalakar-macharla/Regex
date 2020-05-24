import com.manulife.audittrail.StagesExecutionTimeTracker
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

// This code is used to archive and restore complete mongodb database or selected collections.
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
                        logger.info("logging level=${params.loggingLevel}")
                        logger.info("action=${params.action}")
                        localBranchName = GitLabUtils.getLocalBranchName(this)
                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-mongodb.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }
                        unix = isUnix()
                        batsh = unix ? this.&sh : this.&bat
                        logger.debug('EnvironmentVariables: ') { batsh 'env' }
                        GitLabUtils.postStatus(this, 'running')
                        collections = []
                        stagesExecutionTimeTracker.initStageEnd()
                    }
                }
            }
            stage('Download files') {
                when { expression { return ( params.action == 'ARCHIVE')  } }
                environment {
                    onboarding_mongodb_credentials = credentials("${pipelineParams.mongodb_credentials}")
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Start of Download files.')
                        if (pipelineParams.mongodb_collections != null) {
                            collections = pipelineParams.mongodb_collections.split(',')
                            for (col in collections) {
                                batsh  """mongodump --uri="${onboarding_mongodb_credentials_PSW}"  --collection=${col} --out ."""
                            }
                        }
                        else {
                            batsh  """mongodump --uri="${onboarding_mongodb_credentials_PSW}"  --out ."""
                        }
                        logger.debug('Folder content:') {
                            batsh 'ls'
                        }
                        logger.debug('Finished taking backup.')
                    }
                }
            }
            stage('Archive') {
                when { expression { return ( params.action == 'ARCHIVE')  } }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Started Store in Git repo.')
                        batsh "git add -- ./${pipelineParams.mongodb_database}"
                        batsh "git diff-index --quiet HEAD || git commit -m 'Jenkins automatic update commit' -- ./${pipelineParams.mongodb_database} && git push origin HEAD:${localBranchName}"
                        logger.debug('Finished Store in Git repo.')
                    }
                }
            }
            stage('Restore') {
                when { expression { return ( params.action == 'RESTORE')  } }
                environment {
                    Onboarding_mongodb_credentials = credentials("${pipelineParams.mongodb_credentials}")
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Started restore process.')
                        if (pipelineParams.mongodb_collections != null) {
                            collections = pipelineParams.mongodb_collections.split(',')
                            for (col in collections) {
                                batsh """mongorestore --uri="${Onboarding_mongodb_credentials_PSW}" -d ${pipelineParams.mongodb_database} """ +
                                       """--collection=${col}   ${pipelineParams.mongodb_database}/${col}.bson --drop """
                            }
                        }
                        else {
                            batsh """mongorestore --uri="${Onboarding_mongodb_credentials_PSW}" -d ${pipelineParams.mongodb_database} ${pipelineParams.mongodb_database}/ --drop """
                        }

                        logger.debug('Folder content:') { batsh 'ls' }
                        logger.debug('Finished restoring.')
                    }
                }
            }
        }
        post {
            always {
                script {
                    logger.info('Post Action Execution!')
                    new NotificationsSender(this, pipelineParams).send()
                    GitLabUtils.postStatus(this)
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
            choice(
                name: 'action',
                choices: ['ARCHIVE', 'RESTORE'],
                description: 'Action for database backup and restore.'
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
    propertiesCatalog.addMandatoryProperty('mongodb_credentials', '[ERROR]: Missing mongodb_credentials property value.')
    propertiesCatalog.addOptionalProperty('mongodb_collections',
                                          'Defaulting mongodb_collections property to null value. Can be used to specify a comma separated list of collections to be archived/restored.', null)
    propertiesCatalog.addMandatoryProperty('mongodb_database', '[ERROR]: Missing mongodb_database property value. This is the db name where complete db or collections will be restored.')
    return propertiesCatalog
}