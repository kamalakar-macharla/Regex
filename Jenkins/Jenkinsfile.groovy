

import groovy.io.FileType.*;

_nodeOs  = "";
_linux   = "LINUX";
_windows = "WIN";
_nodeLabel = env.nodeLabel;

/*[GIT]*/
_gitPipelineRepository          =   ""; 
_gitPipelineRepositoryBranch    =   "ui";
_gitPipelineRepositoryFolder    =   "common\\ui"
_gitProjectRepository           =   "";
_gitProjectRepositoryBranchName =   "";

/*[STAGES]*/
_stageArray = [:];

/*[STATES]*/
_success    =  "SUCCESS";
_failure    =  "FAILURE";
_notStarted =  "NOT STARTED";
_unstable   =  "UNSTABLE";

/*[MISC]*/
_dirPipelineRepo      =  "pipeline-dir";
_pipelinePropertyFile =  "pipeline.properties";
_pipelineProps        =  "";
_buildVersion         =  "";
_commonUtils          =  "";
_ExeFile              = "";

//UNICODE
_unicodeAnchor  = "\u2693";
_unicodePoints  = "\u27A1";
_unicodeInfo    = "\u273F";
_unicodeSuccess = "\u2714";
_unicodeError   = "\u2716";
stageDifferentiator = "\n================================================================================\n"


/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*NODE*/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
node(_nodeLabel) {
    try 
    {
        deleteDir();

        init();

        execute();
    }
    catch(Exception e) {
        println e.getMessage();
        println e;
        throw e;
    }
    finally {
        deleteDir();

        dir(_dirProjectRepo)
        {
            deleteDir();
        }
        step([$class: 'AuditPipelinePublisher', enabled: true]);
    }   
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*INITIALIZE*/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def init()
 {
    try 
    {
        _nodeOs = isUnix() ? _linux : _windows;
        
        //initialize utilities: get common pipeline utilities
        clonerepo(_nodeOs, "github.aig.net/commercial-it-devops/pipeline-utilities.git", "master", "");
        _commonUtils = load "./pipeline-utilities/common.groovy";

        executeCommand("mkdir ${_dirPipelineRepo}");
         
        initStages();
        
        _commonUtils.prepareStages(_stageCheckoutPipeline,_stageCheckoutProject,_stageBuildAutomation,_stageBuildManagement,);

        initMessages();
    }
    catch(Exception e) {
        println "${_unicodeError} Error occurred during initialization";
        throw e;
    }
 }

def initStages()
{
    _stageCheckoutPipeline  =   "Checkout Pipeline";
    _stageCheckoutProject   =   "Checkout Project"
    _stageBuildAutomation   =   "Build Automation";
    _stageBuildManagement   =   "Build Management";
}

/*[MESSAGES]*/
def initMessages()
{
    //Checkout-Pipeline
    _messageCheckoutPipelineStart       =   "${_unicodePoints} Checking out build Props...";
    _messageCheckoutPipelineFinished    =   "${_unicodeInfo} Checkout of build Props completed.";
    _errorMessagePipeleineCheckout      =   "${_unicodeError} Error during checkout of build props.";

    //Checkout-Code
    _messageCheckoutSourceStart         =   "${_unicodePoints} Checking out project source code.";
    _messageCheckoutSourceFinished      =   "${_unicodeInfo} Checkout of project source code completed.";
    _errorMessageSourceCheckout         =   "${_unicodeError} Error during checking out code.";

    //Checkout-complete
    _messageCheckoutStageFinished       =   "Checkout stage completed.";

    //error message on directory does not exist
    _erroMessageOnDirNotExists          =   "Error: Target directory does not exist.";
    
    //BuildAutomation
    _messageBuildAutomationStart        =   "${_unicodePoints} Building code...";
    _messageBuildAutomationFinish       =   "${_unicodeInfo} Building code completed.";
    _errorMessageBuildAutomation        =   "${_unicodeError} Error during building of code.";

    //BuildManagement
    _messageBuildManagementStart        =   "${_unicodePoints} Uploading binaries to Artifactory...";
    _messageBuildManagementFinish       =   "${_unicodeInfo} Uploading binaries to Artifactory completed.";
    _errorMessageBuildManagement        =   "${_unicodeError} Error while uploading binaries to Artifactory.";

    //PackageBuild
    _errorMessagePackageBuild           =   "${_unicodeError} Error while sequencing package.";
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*EXECUTE*/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def execute()
{   

    println("${stageDifferentiator}${_unicodeAnchor} Running on ${_nodeOs} node.");

    try 
    {
        /**CHECKOUT-PIPELINE**/
        stage(_stageCheckoutPipeline)
        {
            setCurrentStage(_stageCheckoutPipeline);
            runCheckoutPipeline();
            _stageArray.put(_stageCheckoutPipeline,_success);
        }
        println "${stageDifferentiator}"

        /**CHECKOUT-PROJECT**/
        stage(_stageCheckoutProject)
        {
            setCurrentStage(_stageCheckoutProject);
            runCheckoutProject();           
            _stageArray.put(_stageCheckoutProject,_success);
        }
        println "${stageDifferentiator}"
        
        /**BUILD-AUTOMATION**/
        stage(_stageBuildAutomation)
        {
            setCurrentStage(_stageBuildAutomation);
            runBuildAutomation(); 
            _stageArray.put(_stageBuildAutomation,_success);
        }
        println "${stageDifferentiator}"    

        /**BUILD-MANAGEMENT**/
        stage(_stageBuildManagement)
        {
            setCurrentStage(_stageBuildManagement);
            runBuildManagement(); 
            _stageArray.put(_stageBuildManagement,_success);
        }
        println "${stageDifferentiator}"
    
        triggerNextJob();

        //send success notification
        sendEmailNotification(_success);
    }
    catch(Exception e) 
    {
        //send error notification
        sendEmailNotification(_failure);
        throw e;
    }
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*CHECKOUT-PIPELINE*/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def runCheckoutPipeline()
{
    try 
    {
        println _messageCheckoutPipelineStart;

        dir(_dirPipelineRepo)
        {
            _gitPipelineRepository = env.configRepo;
            _gitPipelineRepositoryBranch = env.configRepoBranch;

            clonerepo(_nodeOs, _gitPipelineRepository, _gitPipelineRepositoryBranch, ".");

            //print files to console
            bat "dir";

            //read property file
            _pipelineProps = readProperties file: "$_gitPipelineRepositoryFolder\\$_pipelinePropertyFile";

            initializePipelineProperties();
        }

        println _messageCheckoutPipelineFinished;
    }
    catch(Exception e) 
    {
        println _errorMessagePipeleineCheckout;
        _stageArray.put(_stageCheckoutPipeline,_failure);
        throw e;
    }   
}

def initializePipelineProperties()
{
    //git
    _gitProjectRepository   = _pipelineProps.GIT_REPO;
    _gitProjectRepositoryBranchName = _pipelineProps.GIT_BRANCH;
    
    //build
    _vbExe                  = _pipelineProps.VB_EXE;
    _solutionFilePath       = _pipelineProps.SOLUTION_FILE_PATH;
    _solutionFileFullPath   = _pipelineProps.SOLUTION_FILE_FULLPATH
    _buildFileName          = _pipelineProps.BUILD_FILE_NAME
    _regionSettings         = _pipelineProps.REGION_SETTING

    //version
    _buildVersion           = "${_pipelineProps.MAJOR_VER}.${_pipelineProps.MINOR_VER}.${_pipelineProps.PATCH}.${env.BUILD_NUMBER}";    

    //artifactory
    _artifactoryServerUrl   = env.ARTIFACTORY_URL;
    _artifactoryWebUrl      = env.ARTIFACTORY_WEB_URI;
    _artifactoryBaseFolder  = _pipelineProps.ARTIFACTORY_BASE_FOLDER;
    _artifactNamePrefix     = _pipelineProps.ARTIFACT_NAME_PREFIX;
    _artifactBuildName      = _pipelineProps.ARTIFACT_BUILD_NAME;
    _artifactoryVirtualRepo = _pipelineProps.ARTIFACTORY_VIRTUAL_REPO;
    _artifactoryPublishRepo = _pipelineProps.ARTIFACTORY_PUBLISH_REPO;    
    _artifactoryTarget      = "${_artifactoryPublishRepo}/${_artifactoryBaseFolder}";
    _artifactExeFile        = "${_artifactNamePrefix}_exe_${_buildVersion}.zip";
    _artifactAppvFile       = "${_artifactNamePrefix}_appv_${_buildVersion}.zip";
    _artifactoryFullPath    = "${_artifactoryServerUrl}/webapp/#/artifacts/browse/tree/General/${_artifactoryPublishRepo}/${_artifactoryBaseFolder}/exe/${_buildVersion}";      
    _artifactoryServer      = Artifactory.server env.ARTIFACTORY_CRED_ID;

    //misc
    _dirProjectRepo         =  "C:\\Temp\\build";
    _emailTo                =  _pipelineProps.EMAIL_TO;
    _emailSubject           =  _pipelineProps.EMAIL_SUBJECT;
    _loggingFolder          =  "C:\\Temp\\vblogs";
    _loggingFile            =  "${_artifactNamePrefix}_${env.configRepoBranch}_${_buildVersion}.log";
     
    println "${_unicodeInfo} Properties retrieved.."
    println "GIT_REPO                   : ${_gitProjectRepository}";
    println "GIT_BRANCH                 : ${_gitProjectRepositoryBranchName}";

    println "ARTIFACTORY_SERVER_URL     : ${_artifactoryServerUrl}";
    println "ARTIFACTORY_BASE_FOLDER    : ${_artifactoryBaseFolder}";
    println "ARTIFACT_NAME_PREFIX       : ${_artifactNamePrefix}";
    println "ARTIFACT_BUILD_NAME        : ${_artifactBuildName}";
    println "ARTIFACTORY_VIRTUAL_REPO   : ${_artifactoryVirtualRepo}";
    println "ARTIFACTORY_PUBLISH_REPO   : ${_artifactoryPublishRepo}";

    println "\n================================================================================\n"
    println "ARTIFACTORY_TARGET         : ${_artifactoryTarget}"
    println "ARTIFACT_EXE_FILE          : ${_artifactExeFile}"
    println "ARTIFACTORY_FULL_PATH      : ${_artifactoryFullPath}"

    println "ARTIFACTORY_SERVER         : ${_artifactoryServer}"
    println "\n================================================================================\n"

}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*CHECKOUT-PROJECT*/                                        
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def runCheckoutProject()
{
    try 
    {
        println _messageCheckoutSourceStart;

        executeCommand("if not exist ${_dirProjectRepo} mkdir ${_dirProjectRepo}");

        dir(_dirProjectRepo)
        {
            deleteDir();

            clonerepo(_nodeOs, _gitProjectRepository, _gitProjectRepositoryBranchName, ".");

            //print files to console
            bat "dir";
        }

        println _messageCheckoutSourceFinished;
    }
    catch(Exception e) 
    {
        println _errorMessageSourceCheckout;
        _stageArray.put(_stageCheckoutProject,_failure);
        throw e;
        
    }
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*BUILD AUTOMATION/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def runBuildAutomation()
{
    try 
    {
        println _messageBuildAutomationStart;

        dir(_dirProjectRepo)
        {

            //this is currently running in C:\Temp. cant compile in jenkins workspace because filepath is too long 
            bat "$_vbExe /m ${_solutionFileFullPath} /out ${_loggingFolder}\\${_loggingFile} & exit %%ERRORLEVEL%%" 
            _ExeFile="${_solutionFilePath}\\${_buildFileName}"

            zip dir: _solutionFilePath, glob: "${_buildFileName}", zipFile: _artifactExeFile; 

        }

        println _messageBuildAutomationFinish;
    }
    catch(Exception e) 
    {
        println _errorMessageBuildAutomation;
        _stageArray.put(_stageBuildAutomation,_failure);
        throw e;
    }
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*BUILD MANAGEMENT/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
def runBuildManagement()
{
    try 
    {
        println _messageBuildManagementStart;

        dir(_dirProjectRepo)
        {
            def _buildInfo = Artifactory.newBuildInfo();

                _buildInfo.env.capture = true;
                _buildInfo.env.collect();
                _buildInfo.name = "${_artifactNamePrefix}-exe";
                _buildInfo.number = _buildVersion;

                doUploadToArtifactory("${_artifactoryTarget}/exe/${_buildVersion}/${_artifactExeFile}", "${_artifactExeFile}", _buildInfo, _artifactoryServer);
                _artifactoryServer.publishBuildInfo _buildInfo
        }

        println _messageBuildManagementFinish;
    }
    catch(Exception e) 
    {
        println _errorMessageBuildManagement;
        _stageArray.put(_stageBuildManagement,_failure);
        throw e;
    }
}

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
                                        /*UTILS*/
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
/*Capture the current build stage in CURRENT_STAGE environment variable*/
def setCurrentStage(String currentStage)
{
    env.CURRENT_STAGE =  currentStage;
}

def isNullOrEmpty(obj)
{  
  return obj==null || obj=="";
}

/*executes shell or bat command. 
* commands should be similar*/
def executeCommand(String cmdStr)
{
    try 
    {
        if(_nodeOs == _windows)
            bat "${cmdStr}";
        else
            sh "${cmdStr}";
    }
    catch(Exception e) 
    {
        println "${_unicodeError} Error occurred while executing command";
        throw e;
    }
}

def clonerepo(nodeOs, gitRepo, branchName, folder)
{
    try
    {
        withCredentials([string(credentialsId: 'comm_git_clone_token', variable: 'comm_git_clone_token')])
        {
            if(nodeOs == 'WIN') 
            {
                bat "git clone -b ${branchName} https://${comm_git_clone_token}@${gitRepo} ${folder}";  
            }
            else
            {
                sh "scl enable rh-git29 -- git clone -b ${branchName} https://${comm_git_clone_token}@${gitRepo}";
            }
        }
        println "Cloned repo - ${gitRepo}, branch - ${branchName}";
    }
    catch(Exception e)
    {
        println "Failed to clone repo - ${gitRepo}";
        throw e;
    }
}

def doUploadToArtifactory(artifactoryTarget,artifactoryPattern,buildInfo,server){
    
    def uploadSpec = """ {
        "files" : [
            {
                "pattern" : "${artifactoryPattern}",
                "target" :  "${artifactoryTarget}"
            }]
    }"""
    
    server.upload(uploadSpec, buildInfo)
}

def sendEmailNotification(status)
{
    println "[sendNotification] Status: ${status}";

    def urlArray = [:];
    _emailNotificationSubject = "${_artifactNamePrefix} Build";

    if(status==_success)
    {
        urlArray.put('Artifacts', _artifactoryFullPath);

        _commonUtils.sendEmailNotification(_success,_emailSubject,
            _buildVersion,"sprint-release-notes.txt",false,_emailTo,urlArray,_stageArray);
    }
    else
    {
        _commonUtils.sendEmailNotification(_failure,_emailSubject,
            _buildVersion,"sprint-release-notes.txt",false,_emailTo,urlArray,_stageArray);
    }
}

def exeDeployment() 
{
        println "Starting deploy job with parameters:";
        println "PipelineRepository : ${_gitPipelineRepository}";
        println "PipelineBranch : ${_gitPipelineRepositoryBranch}";
        println "Exe : ${_ExeFile}";
        println "BuildNode : ${_nodeLabel}";

        build job: '/commercial-it-global-delivery/S3-vb6/dev-ci-cd/utils/deploy-component', parameters: [
            string(name: 'PipelineRepository',value: _gitPipelineRepository), 
            string(name: 'PipelineBranch', value: _gitPipelineRepositoryBranch),
            string(name: 'DeploymentType', value: "Exe"),
            string(name: 'Exe', value: _ExeFile),
            string(name: 'BuildNode', value: _nodeLabel)
            ];
}

def runSequenceJob()
{
    println "Starting sequence job with parameters:";
    println "PipelineRepository : ${_gitPipelineRepository}";
    println "PipelineBranch : ${_gitPipelineRepositoryBranch}";
    println "ExeAritfactURL : ${_artifactoryServerUrl}/${_artifactoryTarget}/exe/${_buildVersion}/${_artifactExeFile}";
    println "BuildNode : ${_nodeLabel}";

    build job: '/commercial-it-global-delivery/S3-vb6/dev-ci-cd/utils/sequence-job', parameters: [
        string(name: 'PipelineRepository',value: _gitPipelineRepository), 
        string(name: 'PipelineBranch', value: _gitPipelineRepositoryBranch),
        string(name: 'ExeAritfactURL', value: "${_artifactoryServerUrl}/${_artifactoryTarget}/exe/${_buildVersion}/${_artifactExeFile}"),
        string(name: 'BuildNode', value: _nodeLabel)
        ];

}

def appvDeployment() 
{
        println "Starting deploy job with parameters:";
        println "PipelineRepository : ${_gitPipelineRepository}";
        println "PipelineBranch : ${_gitPipelineRepositoryBranch}";
        println "AppvUrl : ${_artifactoryServerUrl}/${_artifactoryTarget}/exe/${_buildVersion}/${_artifactAppvFile}";
        println "BuildNode : ${_nodeLabel}";

        build job: '/commercial-it-global-delivery/S3-vb6/dev-ci-cd/utils/deploy-component', parameters: [
            string(name: 'PipelineRepository',value: _gitPipelineRepository), 
            string(name: 'PipelineBranch', value: _gitPipelineRepositoryBranch),
            string(name: 'DeploymentType', value: "Appv"),
            string(name: 'AppvArtifactUrl', value: "${_artifactoryServerUrl}/${_artifactoryTarget}/appv/${_buildVersion}/${_artifactAppvFile}"),
            string(name: 'BuildNode', value: _nodeLabel)
            ];
}

def triggerNextJob()
{
    def Build_Options = [Deploy_Exe: Deploy_Exe, Sequence: Sequence_App, Deploy_Appv: Deploy_Appv];

    println "Build options selected:";
    println Build_Options;
    
    if(Build_Options.Deploy_Exe == "true"){
        println "Exe deployment triggered";
        exeDeployment();
    }
    if(Build_Options.Sequence == "true"){
        println "Sequence triggered";
        runSequenceJob();
    }
    if(Build_Options.Deploy_Appv == "true"){
        println "Appv deployment triggered"; 

        //make sure the sequence was triggered first
        if(Build_Options.Sequence == "false"){
            println "Sequence option must be selected in order to deploy a new package";
            return false;
        }    
        else{
        //appvDeployment();
        }
    }

}
