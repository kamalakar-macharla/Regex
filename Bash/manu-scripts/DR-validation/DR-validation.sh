#!/bin/bash

dos2unix reposlist.txt
rm -rf ./*/
sleep 2
echo '' > output.txt
while read REPO
do
	echo "repo name is : ${REPO}"
    git clone https://git.platform.my_mkr_repo/my-dept-devops-test/${REPO}.git
    cd ./${REPO}; git checkout prod; cd ..
    echo "------------- $REPO --------------" >> output.txt
    grep '^fly' ./${REPO}/concourse_pipeline/updatePipeline.sh >> output.txt
    echo "------------- End-----------------" >> output.txt
    echo -e "\n" >> output.txt
done < reposlist.txt



#RES=$(grep -i 'slackChannel' ./${REPO}/jenkins/common-deploy.properties)
#echo "${REPO}  ->  ${RES}" >> output.txt
# grep -i 'build-DR-real' ./${REPO}/concourse_pipeline/pipeline.yml > /dev/null && echo "${REPO} : pipeline.yml having build-DR-real job"  >> output.txt
# [ -f ./${REPO}/concourse_pipeline/updatePipelineDR.sh ] && echo "${REPO} : updatePipelineDR.sh exist" >> output.txt || echo "${REPO} : updatePipelineDR.sh does not exist" >> output.txt
# [ -f ./${REPO}/concourse/common/assemble-dr.yml ] && echo "${REPO} : assemble-dr.yml exist" >> output.txt || echo "${REPO} : assemble-dr.yml does not exist" >> output.txt
# echo ' ' >> output.txt
# start chrome https://concourse.platform.my_mkr_repo/teams/INSTITUTIONAL-GRS/pipelines/concourse-${REPO}?group=DR