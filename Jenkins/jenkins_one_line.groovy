https://github.com/pipelineascodecourse/source_code

env.TAG_NAME
env.UNIX = isUnix()     #returns true if unix like systems
env.BRANCH_NAME

NODELEBEL passed from the jenkins job.
node('env.NODELEBEL'){ 
    stage('build'){
      bat "command";
      sh "command";
      powershell '''    '''
    }
}

parameterlised build - string , choice parameter

Basic steps

isUnix
    deleteDir
    dir
        pwd
        fileExists
            readFile
            writeFile
                archive
                unarchive


echo         # this echo mentiond in jenkins basic steps doc
error
sleep
retry
timeout
-------------------------------------------------------
withEnv
withCredentials

withEnv([
  "ter_host=${THOST}",
  "cr_user=${CUSER}",
  ]){
    sh '${ter_host}'
}

Credentials Binding Plugin
withCredentials([
          usernamePassword(credentialsId: 's3_za_qauat_new', usernameVariable: 'USER_qauat', passwordVariable: 'PWD_qauat'),
          usernamePassword(credentialsId: 's3_za_prod', usernameVariable: 'USER_prod', passwordVariable: 'PWD_prod')
        ]) {
---------------------------------------------------------

import groovy.io.FileType.*;
def myvar // def accept any time of object.

node(_nodeLabel) {
  deleteDir();
  init();
  execute();
}

-----------------------------------------------------------------
env.UNIX = isUnix()
    if (Boolean.valueOf(env.UNIX)) {
        sh cmd
    }
    else {
        bat cmd
   }
isUnix: Checks if running on a Unix-like node
Returns true if enclosing node is running on a Unix-like system 
(such as Linux or Mac OS X), false if Windows.
------------------------------------------------------------------
_commonUtils = load "./pipeline-utilities/common.groovy";
_commonUtils.prepareStages(_stageCheckoutPipeline,_stageCheckoutProject,_stageBuildAutomation,_stageBuildManagement,);


_pipelineProps = readProperties file: "$_dirPipelineRepo\\$_gitPipelineRepositoryFolder\\$_pipelinePropertyFile";
env.projDesc = _pipelineProps.PROJECT_NAME;

 build job: '/globe/S3-vb6/dev-ci-cd/utils/deploy-component', parameters: [
            string(name: 'PipelineRepository',value: _gitPipelineRepository),
            string(name: 'PipelineBranch', value: _gitPipelineRepositoryBranch),
            string(name: 'DeploymentType', value: "Exe"),
            string(name: 'Exe', value: _ExeFile),
            string(name: 'BuildNode', value: _nodeLabel)
            ];

node(_nodeLabel) {
    try
    {
    }
    catch(Exception e) {
        println e;
        throw e;
    }
    finally {
        deleteDir();
    }
}


def server = Artifactory.server 'my-server-id'
If your Artifactory is not defined in Jenkins you can still create it as follows:
def server = Artifactory.newServer url: 'artifactory-url', username: 'username', password: 'password'

def downloadSpec = """{
 "files": [
  {
      "pattern": "bazinga-repo/*.zip",
      "target": "bazinga/"
    }
 ]
}"""
server.download spec: downloadSpec

def uploadSpec = """{
  "files": [
    {
      "pattern": "bazinga/*froggy*.zip",
      "target": "bazinga-repo/froggy-files/"
    }
 ]
}"""
server.upload spec: uploadSpec

server.download spec: downloadSpec, failNoOp: true
server.upload spec: uploadSpec, failNoOp: true

def runner = load pwd() + '/first.groovy'

------- load comman----- dsl script---
You can return a new instance of the class via the load command and use the object to call "doStuff"
So, you would have this in "Thing.groovy"

class Thing {
   def doStuff() { 
     return "HI" 
    }
}
return new Thing();

And you would have this in your dsl script:

node {
   def thing = load 'Thing.groovy'
   echo thing.doStuff()
}
-----------------------------------------

readFile: Read file from workspace

--------- jenkinsci/pipeline-utility-steps-plugin ------------
findFiles
touch
zip
unzip
readProperties

_pipelineProps = readProperties file: "$_dirPipelineRepo\\$_gitPipelineRepositoryFolder\\$_pipelinePropertyFile";
env.projDesc = _pipelineProps.PROJECT_NAME;

https://plugins.jenkins.io/
pipeline: groovy
pipeline: Pipeline Utility Steps
SonarQube Scanner
SonarQuality gates
Docker
Docker pipeline
Docker Compose Build Step
Artifactory
Azure commons



def dirpath = "RELEASE/abc/${abc-version}"
dir(dirpath){
  //logic
}

if (fileExists('file')) {
    echo 'Yes'
} else {
    echo 'No'
}

node {
    try {
        sh 'might fail'
    } catch (err) {
        echo "Caught: ${err}"
        currentBuild.result = 'FAILURE'
    }
    step([$class: 'Mailer', recipients: 'admin@somewhere'])
}

archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true

---------------------------------------------------
multibranch pipeline   https://www.udemy.com/jenkins-pipeline-as-code-all-you-need-to-know-from-a-z/learn/lecture/10999666#overview
node{
	if(env.BRANCH_NAME == 'master'){
		echo "building master"	
	}
	if(env.BRANCH_NAME == 'dev'){
		echo "building dev"	
	}
}

multibranch building tags  https://www.udemy.com/jenkins-pipeline-as-code-all-you-need-to-know-from-a-z/learn/lecture/10999672#overview
node{
	stage("Build"){
		if(env.TAG_NAME != null ){
			println("we are building tag and tag name is ${env.TAG_NAME}")
		}else{
			println("we are building a branch")		
		}
		if(env.TAG_NAME == "release-1.0"){
			println("we are building specifically release-1.0 tag")
		}
	}
}

------------------------------------------------------------------------------
node{
	stage('Build'){
		retry(3){
			error "Error statement just got executed"
		}
	}
}

