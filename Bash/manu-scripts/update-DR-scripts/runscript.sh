#!/bin/bash
BRANCH=prod
#GROUP='my-dept'
GROUP='my-dept-devops-test'
dos2unix reposlist.txt
sed -i '/^$/d' reposlist.txt
rm -rf ./*/

echo "BRANCH is : ${BRANCH}" > output.txt

# clone the repos
while read REPO
do
	echo
	git clone -b ${BRANCH} https://git.platform.my_mkr_repo/${GROUP}/${REPO}.git
	mkdir -p update/${REPO}
	cp ${REPO}/concourse_pipeline/pipeline.yml ./update/${REPO}
	
	
done < reposlist.txt