#!/bin/bash +x
BRANCH=dev
GROUP='my-dept'
#GROUP='my-dept-devops-test'
dos2unix reposlist.txt
sed -i '/^$/d' reposlist.txt
rm -rf ./*/

echo "BRANCH is : ${BRANCH}" > output.txt

# clone the repos
while read REPO
do
	echo
	git clone https://git.platform.my_mkr_repo/${GROUP}/${REPO}.git
	
	cd ./${REPO}
	git checkout dev
	git branch jenkins-params-update
	git checkout jenkins-params-update
	cd ..
	
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/common-ci.properties
	cat ../std-files/common-ci.properties >> ./${REPO}/jenkins/common-ci.properties
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/dev-ci.jenkinsfile
	cat ../std-files/dev-ci.jenkinsfile >> ./${REPO}/jenkins/dev-ci.jenkinsfile
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/dev-ci.properties
	cat ../std-files/dev-ci.properties >> ./${REPO}/jenkins/dev-ci.properties	
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/tst-ci.jenkinsfile
	cat ../std-files/tst-ci.jenkinsfile >> ./${REPO}/jenkins/tst-ci.jenkinsfile
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/tst-ci.properties
	cat ../std-files/tst-ci.properties >> ./${REPO}/jenkins/tst-ci.properties
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/uat-ci.jenkinsfile
	cat ../std-files/uat-ci.jenkinsfile >> ./${REPO}/jenkins/uat-ci.jenkinsfile
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/uat-ci.properties
	cat ../std-files/uat-ci.properties >> ./${REPO}/jenkins/uat-ci.properties
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/prod-ci.jenkinsfile
	cat ../std-files/prod-ci.jenkinsfile >> ./${REPO}/jenkins/prod-ci.jenkinsfile
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/prod-ci.properties
	cat ../std-files/prod-ci.properties >> ./${REPO}/jenkins/prod-ci.properties
	
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/feature-ci.jenkinsfile
	cat ../std-files/feature-ci.jenkinsfile >> ./${REPO}/jenkins/feature-ci.jenkinsfile
	echo -e "\n  ---------------------" >> ./${REPO}/jenkins/feature-ci.properties
	cat ../std-files/feature-ci.properties >> ./${REPO}/jenkins/feature-ci.properties
	
	sed -i "s/XXXXX/${REPO}/g" ./${REPO}/jenkins/*.*
	
	LTN="./${REPO}/jenkins"
	notepad++ ${LTN}/common-ci.properties
	
	notepad++ ${LTN}/dev-ci.jenkinsfile
	notepad++ ${LTN}/dev-ci.properties
	
	notepad++ ${LTN}/tst-ci.jenkinsfile
	notepad++ ${LTN}/tst-ci.properties
	
	notepad++ ${LTN}/uat-ci.jenkinsfile
	notepad++ ${LTN}/uat-ci.properties
	
	notepad++ ${LTN}/prod-ci.jenkinsfile
	notepad++ ${LTN}/prod-ci.properties
	
	notepad++ ${LTN}/feature-ci.jenkinsfile
	notepad++ ${LTN}/feature-ci.properties	
	
	
done < reposlist.txt

# git add .;git commit -m 'adding new properties into jenkins files';git push origin jenkins-params-update
	# git add .
	# git commit -m 'adding new properties into jenkins files'
	# git push origin jenkins-params-update


