#!/bin/bash
BRANCH=dev
#GROUP='cdn-institutional-grs'
GROUP='cdn-institutional-grs-devops-test'
dos2unix reposlist.txt
sed -i '/^$/d' reposlist.txt
rm -rf ./*/

echo "BRANCH is : ${BRANCH}" > output.txt

# clone the repos
while read REPO
do
	echo
	git clone -b ${BRANCH} https://git.platform.manulife.io/${GROUP}/${REPO}.git
	
	
done < reposlist.txt