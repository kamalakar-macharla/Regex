import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.audittrail.PipelineRunAuditTrailing
import com.manulife.banner.Banner
import com.manulife.gitlab.GitLabPropertiesCalalogBuilder
import com.manulife.logger.Level
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
import com.manulife.util.Shell

def call(Map configuration) {
    pipeline {
        agent {
            label "${configuration.jenkinsJobInitialAgent}"
        }
        stages {
            stage('Read Parameters') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        new ParametersReport(this, params).print()
                        new ConfigurationReport(this, configuration).print()
                        new Banner(this).print()

                        Level loggingLevel = params.loggingLevel
                        logger = new Logger(this, loggingLevel)

                        // Read property files
                        pipelineParams = new Properties()
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-migration.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }
                        unix = isUnix()
                        Shell.fixAndPropagateJavaHome(this, unix)
                        Shell.trustZscalerInJava(this, unix)
                    }
                }
            }
            stage ('Create Database') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.warning('Not supported for now.  Please create the database manually if it doesn\'t exist yet.')
                    }
                }
            }
            stage('Execute flyway migration') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        logger.debug('Root Folder Content') { sh 'ls -al' }

                        if (Level.DEBUG == logger.level) {
                            String locations = pipelineParams.locations.replaceAll('filesystem:', '')
                            def folders = locations.split(',')
                            for (String folder : folders) {
                                logger.debug("Content of ${folder}:") { sh "ls -al ${folder}" }
                            }
                        }

                        flywayExtraParams = ''
                        if (pipelineParams.extraFlywayParams != null) {
                            flywayExtraParams += pipelineParams.extraFlywayParams
                        }

                        flywayVer = '6.0.4'
                        flywayRoot = "${HOME}/Home/workspace"
                        flywayDir = "${flywayRoot}/flyway-${flywayVer}"
                        flywayScript = "${flywayDir}/flyway"

                        def flywayTarBallName = "flyway-commandline-${flywayVer}-macosx-x64.tar.gz"

                        if (!fileExists(flywayScript)) {
                            final RTFACT_8639_RESOLVED = false
                            if (RTFACT_8639_RESOLVED) {
                                artifactoryServer = Artifactory.server('Artifactory-Global-Prod')
                                ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                                def downloadRepo = 'maven-remote-cache'
                                def flywayRootSlash = artifactoryHelper.simpleDownload(downloadRepo,
                                                                                       "org/flywaydb/flyway-commandline/${flywayVer}/${flywayTarBallName}",
                                                                                       "${flywayRoot}/",
                                                                                       true)
                                if (!flywayRootSlash) {
                                    error "Failed to download ${flywayTarBallName} from Artifactory repo \"${downloadRepo}\"; see also https://www.jfrog.com/jira/browse/RTFACT-8639"
                                }

                                if (!fileExists(flywayDir)) {
                                    error "Failed to unpack the tarball to ${flywayDir}"
                                }
                            }
                            else {
                                def artUrl = 'https://artifactory.platform.manulife.io/artifactory'
                                def artRepo = 'maven-remote'
                                def flywayArtPath = "org/flywaydb/flyway-commandline/${flywayVer}/${flywayTarBallName}"

                                sh """
                                    set -ex
                                    cd "${flywayRoot}"
                                    curl -sS -o "${flywayTarBallName}" "${artUrl}/${artRepo}/${flywayArtPath}"
                                    tar -xvzf "${flywayTarBallName}"
                                    rm -vf "${flywayTarBallName}"
                                    chmod 0755 "${flywayScript}"
                                    rm -rf "${flywayDir}/jre"
                                """
                            }

                            logger.debug('Folder content: ') { sh "ls -alR ${flywayDir}" }

                            if (!fileExists(flywayScript)) {
                                error "Failed to find a script ${flywayScript}"
                            }
                        }

                        logger.debug('Folder content: ') { sh "ls -al ${flywayDir}" }

                        try {
                            withCredentials([usernamePassword(credentialsId: "${pipelineParams.dbCredentials}",
                                    usernameVariable: 'DB_USR', passwordVariable: 'DB_PSW')]) {
                                sh """
                                    ${flywayScript} -user='${DB_USR}' -password='${DB_PSW}' \
-url='${pipelineParams.url}' -locations='${pipelineParams.locations}' ${flywayExtraParams} migrate
                                """
                            }
                        }
                        catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                            error("Unable to find a valid entry in the Jenkins credentials vault with id \"${pipelineParams.dbCredentials}\": ${e}")
                        }
                    }
                }
            }
            stage('Trigger Deployment Pipeline') {
                when { expression { return pipelineParams.deploymentJenkinsJobName } }
                steps {
                    build job: "${pipelineParams.deploymentJenkinsJobName}",
                          wait: false,
                          parameters: [[$class: 'StringParameterValue', name: 'commit_id', value: "${GIT_COMMIT}"]]
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
            booleanParam(
                name: 'debug_mode',
                defaultValue: false,
                description: 'Allows execution of the pipeline in debug mode which will display the content of the project workspace root folder.'
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

    propertiesCatalog.addMandatoryProperty('url',
                                           'Missing jdbc url to connect to the database.  ' +
                                             'Should be similar to: jdbc:mysql://localhost:3306/devday?useUnicode=true&characterEncoding=utf8&useSSL=false')
    propertiesCatalog.addMandatoryProperty('dbCredentials',
                                           'Jenkins credential ID that contains a username/password type to authenicate into the DB')

    propertiesCatalog.addOptionalProperty('locations',
                                          'Defaulting the location of the migration scripts to \"filesystem:./db/migration/common\"',
                                          'filesystem:./db/migration/common')
    propertiesCatalog.addOptionalProperty('extraFlywayParams',
                                          'Defaulting the extraFlywayParams to null.  Can be used to pass additional parameter to the flyway call',
                                          null)
    propertiesCatalog.addOptionalProperty('deploymentJenkinsJobName',
                                          'Defaulting deploymentJenkinsJobName to null.  ' +
                                            'Could be set to the path/Name of the Deployment Jenkins job to be triggered after the execution of this migration pipeline.',
                                          null)

    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.FLYWAY)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
