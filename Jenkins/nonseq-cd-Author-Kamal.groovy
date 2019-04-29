// Author : Kamalakar

import groovy.io.FileType.*;
_nodeLabel = env.nodeLabel;

/*[GIT]*/
_gitPipelineRepository = env.configRepo;
_gitPipelineRepositoryBranch = env.configRepoBranch;
_gitPipelineRepositoryFolder =  "common\\ui"
_pipelinePropertyFile =  "pipeline.properties";

jobName = env.JOB_NAME;
envName = env.PL_ENV_NAME;
compName = env.PL_COMP_NAME;
_windows = "WIN";
_dirPipelineRepo = "pipeline-dir";
_commonUtils = "";
_stageArray = [:];
def uiSourceRepo;
def uiTargetRepo;
def artifactoryServer;
_pipelineProps="";
_emailTo="";
_emailSubject="";
_success    =  "SUCCESS";
_failure    =  "FAILURE";
node(_nodeLabel) {
  deleteDir();
  init();
  execute();
}

def init()
 {
    try {
        _nodeOs = isUnix() ? _linux : _windows;
        //initialize utilities: get common pipeline utilities
        clonerepo(_nodeOs, "github.aig.net/commercial-it-devops/pipeline-utilities.git", "master", "");
        _commonUtils = load "./pipeline-utilities/common.groovy";
        bat "mkdir ${_dirPipelineRepo}"
        _commonUtils.prepareStages('Promotion', 'Deployment', 'Notification');
        clonerepo(_nodeOs, _gitPipelineRepository, _gitPipelineRepositoryBranch, "${_dirPipelineRepo}");
        _pipelineProps = readProperties file: "$_dirPipelineRepo\\$_gitPipelineRepositoryFolder\\$_pipelinePropertyFile";
        env.projDesc = _pipelineProps.PROJECT_NAME;
        _emailTo = _pipelineProps.EMAIL_TO;
        _emailSubject = _pipelineProps.EMAIL_SUBJECT;
    }
    catch(Exception e) {
        println " Error occurred during initialization";
        throw e;
    }
 }

def execute() {
    def sourcefile;
    artifactoryServer = Artifactory.server env.ARTIFACTORY_CRED_ID;
    //Define pipeline stages
    //def stageArray = _pipelineProps.prepareStages(compName.toUpperCase() + ' Promotion', compName.toUpperCase() + ' Deployment');
    //Get user details who triggered the build
    getBuildUserDetails();
    currentPromotionDetails = [env.BUILD_USER, env.BUILD_USER_ID, env.BUILD_USER_EMAIL, env.BUILD_NUMBER, env.buildVersion].join(',')
    println "Promotion details:\n ${currentPromotionDetails}"
    promoteComment = "Promote Exe"
    if (envName.toUpperCase().contains('QA')) {
        uiTargetRepo = "commercial-it-dotnet-qa-local";
        uiSourceRepo = "commercial-it-dotnet-dev-local";
    }
    if (envName.toUpperCase().contains('UAT')) {
        uiTargetRepo = "commercial-it-dotnet-uat-local";
        uiSourceRepo = "commercial-it-dotnet-qa-local";
    }
    if (envName.toUpperCase().contains('PROD')) {
        uiTargetRepo = "commercial-it-dotnet-prod-local";
        uiSourceRepo = "commercial-it-dotnet-uat-local";
    }
    stage('Promotion'){
      setCurrentStage('Promotion');
      promoteBuild(artifactoryServer, env.artifactName, env.buildVersion, 'Released', promoteComment, uiSourceRepo, uiTargetRepo);
      _stageArray.put(env.CURRENT_STAGE,_success);
    }
    def artifactDownloadPattern = "\"${uiTargetRepo}/com/aig/commercial/global-delivery/${env.projDesc.toLowerCase()}/ui/exe/${env.buildVersion}/*.zip\""
    println "artifactDownloadPattern = ${artifactDownloadPattern}"
    sourcefile = doDownloadFromArtifactory(artifactDownloadPattern,artifactoryServer);
    ExeDeployHandler(_pipelineProps.DES_DIR);
}

def promoteBuild(server, buildname, buildno, comment, status, sourceRepo, targetRepo){
    println sourceRepo
    println targetRepo
    println buildname
    println buildno
    println comment
    println status

    println "${sourceRepo}${buildname}${buildno}"
    def buildInfo   = Artifactory.newBuildInfo();
    buildInfo.name  = buildname;
    buildInfo.number= buildno;
    def promotionConfig = [
          //Mandatory parameters
          'buildName'  : buildInfo.name,
          'buildNumber': buildInfo.number,
          'targetRepo' : targetRepo,

          //Optional parameters
          'comment'    : comment,
          'sourceRepo' : sourceRepo,
          'status'     : status,
          'includeDependencies': false,
          'failFast'   : true,
          'copy'       : false
        ]

    // Promote build
    server.promote promotionConfig
    _stageArray.put(env.CURRENT_STAGE,_success);
}

def getBuildUserDetails() {
    if (_nodeOs == "LINUX")
        wrap([$class: 'BuildUser']) {
            env.BUILD_USER = "${BUILD_USER}"
            env.BUILD_USER = env.BUILD_USER.replace(',', " ");
            env.BUILD_USER_ID = "${BUILD_USER_ID}"
            env.BUILD_USER_EMAIL = "${BUILD_USER_EMAIL}"
        }
    if (_nodeOs == "WIN")
        wrap([$class: 'BuildUser']) {
            env.BUILD_USER = BUILD_USER
            env.BUILD_USER_ID = BUILD_USER_ID
            env.BUILD_USER_EMAIL = BUILD_USER_EMAIL
        }
}

def doDownloadFromArtifactory(artifactDownloadPattern,artifactoryServer){
    println "downloading"
    println "${artifactDownloadPattern}"
     downloadSpec= """{
            "files": [
                {
                "pattern": ${artifactDownloadPattern},
                "target": "./"
                }
            ]
            }"""
    artifactoryServer.download spec: downloadSpec
    def foundfiles = findFiles(glob: '**/*.zip')
    echo "${foundfiles[0].name}"
    echo "${foundfiles[0].path}"
    unzip zipFile: "${foundfiles[0].path}"
    def SourceExeFile = findFiles(glob: '**/*.exe')
    echo "${SourceExeFile[0].name}"
    echo "${SourceExeFile[0].path}"
    def exefile = "${SourceExeFile[0].path}"
    echo "exefile= ${exefile}"
    return exefile
}

def ExeDeployHandler(Des_DIR){
    try{
      stage('Deployment'){
        setCurrentStage('Deployment');
        withCredentials([
          usernamePassword(credentialsId: 's3_za_qauat_new', usernameVariable: 'USER_qauat', passwordVariable: 'PWD_qauat'),
          usernamePassword(credentialsId: 's3_za_prod', usernameVariable: 'USER_prod', passwordVariable: 'PWD_prod')
        ]) {
          if (envName == 'qa' || envName == 'uat') {
            CUSER = "${env.USER_qauat}"
            CPWD = "${env.PWD_qauat}"
            if (envName == 'qa'){
              THOST = "${_pipelineProps.QA_TERMINAL_SERVER}${_pipelineProps.TER_SER_DOMAIN}";
            }
            if (envName == 'uat'){
              THOST = "${_pipelineProps.UAT_TERMINAL_SERVER}${_pipelineProps.TER_SER_DOMAIN}";
            }
            CopyExeToServer(THOST,CUSER,CPWD,Des_DIR)
          }
          if (envName == 'prod') {
            CUSER = "${env.USER_prod}"
            CPWD = "${env.PWD_prod}"
            String ProdHostList = "${_pipelineProps.PROD_TERMINAL_SERVER}";
            println "ProdHostList = ${ProdHostList}"
            String[] strg = ProdHostList.split(',');
              for ( int i = 0; i < strg.size(); i++ ) {
               println strg[i];
               ivar = strg[i];
               THOST = "${ivar}${_pipelineProps.TER_SER_DOMAIN}";
               CopyExeToServer(THOST,CUSER,CPWD,Des_DIR)
             }
          }
        }
        _stageArray.put(env.CURRENT_STAGE,_success);
      }
      stage('Notification') {
        sendEmailNotification(_success);
      }
    }catch(Exception e){
      promoteComment = "Demote Exe"
      println "Demoting the build"
      promoteBuild(artifactoryServer, env.artifactName, env.buildVersion, 'Demote', promoteComment, uiTargetRepo, uiSourceRepo);
      stage('Notification') {
        sendEmailNotification(_failure);
      }
      throw e;
    }
}
def CopyExeToServer(THOST,CUSER,CPWD,Des_DIR){
  	withEnv(["ter_host=${THOST}","cr_user=${CUSER}","cr_pwd=${CPWD}","des_dir=${Des_DIR}"]) {
  		powershell '''
        $nhost = "$env:ter_host"
    		$pw = convertto-securestring -AsPlainText -Force -String $env:cr_pwd
    		$RM_cred = New-Object System.Management.Automation.PSCredential ("R3-CORE\\$env:cr_user",$pw)
    		$Server = New-PSSession -ComputerName $nhost -Credential $RM_cred
    		Invoke-Command -Session $Server -ScriptBlock {
      		whoami
      		$env:computername
    		}
    		Copy-Item -Path .\\*.exe -Destination "$env:des_dir" -ToSession $Server -ErrorAction Ignore
    		Get-PSSession | Remove-PSSession
  		'''
    	}
}


def clonerepo(_nodeOs, gitRepo, branchName, folder) {
    try {
        withCredentials([string(credentialsId: 'comm_git_clone_token', variable: 'comm_git_clone_token')]) {
            if(_nodeOs == 'WIN') {
                bat "git clone -b ${branchName} https://${comm_git_clone_token}@${gitRepo} ${folder}";
            }
            else {
                sh "scl enable rh-git29 -- git clone -b ${branchName} https://${comm_git_clone_token}@${gitRepo}";
            }
        }
        println "Cloned repo - ${gitRepo}, branch - ${branchName}";
    }
    catch(Exception e) {
        println "Failed to clone repo - ${gitRepo}";
        throw e;
    }
}

def setCurrentStage(String currentStage)
{
    env.CURRENT_STAGE =  currentStage;
}

def sendEmailNotification(status){
    println "[sendNotification] Status: ${status}";
    def urlArray = [:];
    _emailNotificationSubject = "";
    if(status==_success){
        _artifactoryFullPath=""
        urlArray.put('Artifacts', _artifactoryFullPath);
        _commonUtils.sendEmailNotification(_success,_emailSubject,
			env.buildVersion,"sprint-release-notes.txt",false,_emailTo,urlArray,_stageArray);
    }
    else {
        _commonUtils.sendEmailNotification(_failure,_emailSubject,
			env.buildVersion,"sprint-release-notes.txt",false,_emailTo,urlArray,_stageArray);
    }
}
