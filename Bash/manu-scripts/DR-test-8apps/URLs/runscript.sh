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
	# Notepad++ ${REPO}/concourse_pipeline/pipeline.yml
	sleep 1
	# echo "------------  ${REPO}  ------------------" >> ./command-file.txt
	# DR | sed 's/xxxxx/${REPO}/g' >> ./command-file.txt
	# echo "" >> ./command-file.txt
	
	# start chrome https://concourse.platform.my_mkr_repo/teams/INSTITUTIONAL-GRS/pipelines/concourse-${REPO}
	
	echo "------------  ${REPO}  ------------------" >> ./URLs.txt
	echo "https://concourse.platform.my_mkr_repo/teams/INSTITUTIONAL-GRS/pipelines/concourse-${REPO}/jobs/build-DR-delete" >> ./URLs.txt
	echo "https://concourse.platform.my_mkr_repo/teams/INSTITUTIONAL-GRS/pipelines/concourse-${REPO}/jobs/build-prod-restart" >> ./URLs.txt
	echo "https://concourse.platform.my_mkr_repo/teams/INSTITUTIONAL-GRS/pipelines/concourse-${REPO}/jobs/build-prod-delete" >> ./URLs.txt
	echo "" >> ./URLs.txt
	
done < reposlist.txt