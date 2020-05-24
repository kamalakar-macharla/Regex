#!/bin/bash
read -p 'Enter jenkins URL : ' JENKINS_URL
read -p 'Enter App Name : ' APP_NAME

rm -rf ./*/

# curl -s -S -u machaka:Memo@123 https://jenkins.manulife.com/job/GRS_Projects/job/GRS_GRSPlus/job/GRS_product/job/GRS_product_DEV_Deploy/204/consoleText > output.txt
curl -s -S -u machaka:Cosmos@123 $JENKINS_URL > output.txt
grep -i 'jfrog rt' output.txt > jfrog_artifactory.txt
grep -i 'git.exe' output.txt > git.txt
grep -i 'fly ' output.txt > fly.txt
grep -i 'cloning' output.txt > cloning_repos.txt
grep -i 'common/scripts' output.txt > common_scripts.txt
grep -i 'kafka' output.txt > kafka.txt
grep -i 'Checking out Revision' output.txt > Checking_out_Revision.txt
grep -i 'pcf' output.txt > pcf.txt
grep -i 'cf ' output.txt >> pcf.txt

git clone https://git.platform.my_mkr_repo/my-dept-devops-test/${APP_NAME}.git ${APP_NAME}_SecretRepo
git clone https://git.platform.my_mkr_repo/my-dept/${APP_NAME}.git ${APP_NAME}_AppRepo
git clone https://git.platform.my_mkr_repo/CDT_Common/JenkinsGenericPipelines.git