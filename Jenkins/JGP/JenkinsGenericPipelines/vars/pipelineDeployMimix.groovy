import com.manulife.artifactory.ArtifactoryHelper
import com.manulife.artifactory.ArtifactoryPropertiesCalalogBuilder
import com.manulife.banner.Banner
import com.manulife.git.GitPropertiesCatalogBuilder
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

                        // Artifactory
                        artifactoryServer = Artifactory.server(pipelineParams.artifactoryInstance)
                    }
                }
            }
            stage('Download artifacts') {
                steps {
                    script {
                        try {
                                FAILED_STAGE = env.STAGE_NAME
                                logger.debug('Initializing artifactoryserver')
                                ArtifactoryHelper artifactoryHelper = new ArtifactoryHelper(this, artifactoryServer)
                                logger.info 'downloading artifacts'
                                def downloadLocation = artifactoryHelper.downloadArtifact(GIT_COMMIT,
                                                                pipelineParams.downloadPattern,
                                                                pipelineParams.releaseRepo)
                                logger.info("downloaded artifacts at ${downloadLocation}")
                                logger.debug('----  Content of current Directory  ----')
                                logger.debug('') { sh 'ls -al' }
                                logger.debug('----  Content of data Directory  ----') { sh "ls ${downloadLocation}" }
                                logger.info('Unzipping file...')
                                sh "tar -xzvf ${downloadLocation}${gitlabSourceRepoName}-*.tar.gz"
                                logger.info('Unzip completed')
                        }
                        catch (e) {
                            error("Unable to downlaod the artifacts due to the unexpected error(s), error: ${e.message}")
                        }
                    }
                }
            }
            stage('CIFS') {
                steps {
                    script {
                        try {
                            FAILED_STAGE = env.STAGE_NAME
                            withCredentials([usernamePassword(credentialsId: 'grsplusgitlab', passwordVariable: 'mimix_P', usernameVariable: 'mimix_U')]) {
                                Date latestdate = new Date()
                                logger.debug("currentBuildTime: ${latestdate.format('yyyyMMdd-HHmmss')}")
                                def currentBuildTime = latestdate.format('yyyyMMdd-HHmmss')
                                cifsPublisher(publishers:
                                                [
                                                    [
                                                        configName: 'GRS_ MIMIX',
                                                        transfers: [
                                                            [ cleanRemote: false,
                                                                excludes: '',
                                                                flatten: false,
                                                                makeEmptyDirs: false,
                                                                noDefaultExcludes: false,
                                                                patternSeparator: '[, ]+',
                                                                remoteDirectory: "${pipelineParams.targetLocation}/${currentBuildTime}",
                                                                remoteDirectorySDF: false,
                                                                removePrefix: "${pipelineParams.removePrefix}",
                                                                sourceFiles: "${pipelineParams.sourceFiles}"
                                                            ]
                                                        ],
                                                        usePromotionTimestamp: true,
                                                        useWorkspaceInPromotion: false, verbose: true
                                                    ]
                                                ])
                            }
                        }
                        catch (e) {
                            error("Unable to run the CIFS plugin due to the unexpected error(s), error: ${e.message}")
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    new NotificationsSender(this, pipelineParams).send()
                    new SharedLibraryReport(this).print()
                    new ProductionSupportInfo(this).print()
                }

                logger.debug('clearing work space')
                cleanWs()
                logger.debug('cleared work space')
            }
        }
        parameters {
            string(
                name: 'commit_id',
                defaultValue: 'latest',
                description: 'Git Commit hash to deploy.'
            )
            choice(
                name: 'loggingLevel',
                choices: ['INFO', 'TRACE', 'DEBUG', 'WARNING', 'ERROR', 'FATAL', 'OFF'],
                description: 'Logging level to use in the job console'
            )
            string(
                name: 'gitlabSourceRepoName',
                defaultValue: '',
                description: 'Git Lab Source repository name'
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

    propertiesCatalog.addOptionalProperty('environmentToDeploy', 'Defaulting environment variable where we want to deploy', null)
    propertiesCatalog.addOptionalProperty('targetLocation', 'Defaulting location where we want to copy our RSLS files', null)
    propertiesCatalog.addOptionalProperty('removePrefix', 'Defaulting files that we want to remove from the source file', 'src/Downloaded_Artifacts')
    propertiesCatalog.addOptionalProperty('sourceFiles', 'Default source files that we want to copy to the Target location', '**/Downloaded_Artifacts/*.RSL')

    ArtifactoryPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitPropertiesCatalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    GitLabPropertiesCalalogBuilder.build(propertiesCatalog, PipelineType.SHELLEXEC)
    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
