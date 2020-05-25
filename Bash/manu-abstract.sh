
----------------------
#!/bin/bash
JENKINS_URL='https://jenkins.company-xyz.com/job/Deploy/204/consoleText > output.txt'
read -p 'Enter jenkins URL : ' JENKINS_URL
read -p 'Enter App Name : ' APP_NAME
rm -rf ./*/
curl -s -S -u machaka:Cosmos@123 $JENKINS_URL > output.txt
grep -i 'jfrog rt' output.txt > jfrog_artifactory.txt
grep -i 'git.exe' output.txt > git.txt

--------------
#!/bin/bash -e
BRANCH=dev
GROUP='my-dept'
dos2unix reposlist.txt
sed -i '/^$/d' reposlist.txt  # remove any spaces after the repo name
rm -rf ./*/                   # Remove only directories(not files) from the current directories
# clone the repos
while read REPO
do
	echo
	git clone -b ${BRANCH} https://git.platform.my_mkr_repo/${GROUP}/${REPO}.git	
done < reposlist.txt
-------------------

DR | sed 's/xxxxx/${REPO}/g'
echo -e "\n" >> output.txt

-----------



