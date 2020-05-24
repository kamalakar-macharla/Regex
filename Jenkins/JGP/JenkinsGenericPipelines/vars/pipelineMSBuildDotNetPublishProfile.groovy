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
                        boolean propertiesFileContentValid = PropertyFilesReader.read(this, configuration.propertiesFileName, buildPropertiesCatalog(), 'common-deploy.properties', pipelineParams)
                        if (!propertiesFileContentValid) {
                            currentBuild.result = 'FAILED'
                            error('There are issues in the pipeline properties file content.  More information available in the Job\'s log.')
                        }

                        unix = isUnix()

                        // DotNet Core v. 2.2.301 showed no
                        // StackOverflowException when parsing IMIT MEF
                        // KafkaProducer.csproj with BlackduckNugetInspector,
                        // https://github.com/blackducksoftware/synopsys-detect/issues/53
                        dotNetMajMin = '2.2'
                        dotNetVersion = "${dotNetMajMin}.301"
                        dotNetRoot = "${ProgramFiles}\\dotnet"
                        // dotNetRoot = "${USERPROFILE}\\.dotnet"

                        // dir(pwd(tmp: true)) {
                        //  curl -sSO https://dot.net/v1/dotnet-install.ps1
                        //  powershell -Command .\dotnet-install.ps1 -InstallDir \"%dr%\" -Version \"%dv%\"
                        // }

                        // https://docs.microsoft.com/en-us/dotnet/core/versions/selection#the-sdk-uses-the-latest-installed-version
                        // TODO: consider checking for the file existence and
                        // installing the required DotNet Core version.
                        def globalJsonContents = """{
                        "sdk": {
                            "version": "${dotNetVersion}"
                        }
                        }
                        """
                        logger.debug("Writing a global.json with a DotNet Core version ${dotNetVersion}...")
                        logger.debug("Contents of global.json: ${globalJsonContents}")
                        writeFile file: "${WORKSPACE}/global.json", text: globalJsonContents, encoding: 'UTF-8'

                        env.DOTNET_HOME = dotNetRoot
                        pathsep = unix ? ':' : ';'
                        env.PATH = dotNetRoot + pathsep + env.PATH

                        buildtype = 'Release'
                        projFramework = "netcoreapp${dotNetMajMin}"

                        // When we start using Docker build machines we will
                        // need to remove all the paths since this will be
                        // defined in the container itself.  We will just leave
                        // the name of the exe to call.

                        // Work around other Visual Studios installed on the
                        // same node which results in a build error,
                        //      MSB4236: The SDK 'Microsoft.NET.Sdk.Web' specified could not be found
                        // as well as
                        //      MSB4236: The SDK 'Microsoft.NET.Sdk' specified could not be found.
                        // https://github.com/Microsoft/msbuild/issues/2532
                        env.MSBUILD_EXE_PATH = "${dotNetRoot}\\sdk\\${dotNetVersion}\\MSBuild.dll"
                        env.MSBuildSDKsPath = "${dotNetRoot}\\sdk\\${dotNetVersion}\\Sdks"

                    }
                }
            }
            stage ('Download Package') {
              steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        // Decide which commit id should be used
                        if (params.commit_id == null || commit_id.trim().isEmpty()) {
                            commit_id = "${GIT_COMMIT}"
                        }
                        logger.debug("### Downloading artifacts from commit: `${commit_id}`")

                        def server = Artifactory.server(pipelineParams.artifactoryInstance)

                        def downloadSpec =
                             """{
                                 "files":
                                     [{
                                        "pattern": "${pipelineParams.releaseRepo}/*.nupkg",
                                        "props": "vcs.revision=${commit_id}",
                                        "flat": true,
                                        "target": "artifact.zip"
                                     }]
                            }"""

                        logger.debug("Downloading from Artifactory using the following downloadSpec: ${downloadSpec}")
                        server.download(downloadSpec)

                        if (!fileExists('artifact.zip')) {
                            error "Unable to download a nuget package from Artifactory from ${pipelineParams.releaseRepo} with commit_id ${commit_id}"
                        }

                        logger.debug('Successfully downloaded nuget package from Artifactory!')
                        unzip 'artifact.zip'
                    }
                }
            }
            stage ('Deploy') {
                environment {
                    DEPLOYMENT_CREDENTIALS = credentials("${pipelineParams.deployment_Credentials}")
                }
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        def publishProfiles = pipelineParams?.publishProfiles?.split(',')
                        for (String publishProfile : publishProfiles) {
                            bat "dotnet msbuild /p:DeployOnBuild=true /p:PublishProfile=${publishProfile} /p:UserName=%DEPLOYMENT_CREDENTIALS_USR% /p:Password=%DEPLOYMENT_CREDENTIALS_PSW%"
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

    propertiesCatalog.addMandatoryProperty('deployment_Credentials',
                                           'Missing deployment_Credentials property value.  Should be the name of the entry containing your credentials in the Jenkins credentials vault.')
    propertiesCatalog.addMandatoryProperty('publishProfiles',
                                           'Missing publishProfiles property value.  Should be an comma separated of profiles to be published like: DevWebDeployServer1,DevWebDeployServer2')

    propertiesCatalog.addOptionalProperty('projectRootFolder', 'Defaulting projectRootFolder property to \".\"', '.')

    NotificationsPropertiesCalalogBuilder.build(propertiesCatalog)

    return propertiesCatalog
}
