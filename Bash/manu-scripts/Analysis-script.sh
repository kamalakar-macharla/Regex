#!/bin/bash

APPNAME='transact-api'
fly -t build-dev-${APPNAME} login --team-name INSTITUTIONAL-GRS --concourse-url https://concourse.platform.my_mkr_repo --insecure
git clone -b prod https://git.platform.my_mkr_repo/my-dept/${APPNAME}.git
cd ${APPNAME}
GIT_COMMIT=$(git log -1 --pretty=format:%h)
echo $GIT_COMMIT
cd ../;rm -rf ${APPNAME}

# fly -t build-dev-${APPNAME} login --team-name INSTITUTIONAL-GRS --concourse-url https://concourse.platform.my_mkr_repo --insecure
BUILD_NO=$(fly -t build-dev-${APPNAME} builds -p concourse-${APPNAME} | grep "prod-${APPNAME}" | grep 'succeeded' | head -1 | cut -d ' ' -f 1)
echo "BUILD_NO : ${BUILD_NO}"
fly -t build-dev-${APPNAME} watch -b "${BUILD_NO}" | grep -i "$GIT_COMMIT"

# fly -t build-dev-${APPNAME} login --team-name INSTITUTIONAL-GRS -u "grsplusgitlab" -p "q$$EJs21" --concourse-url https://concourse.platform.my_mkr_repo --insecure

# username-grsplusgitlab
# q$$EJs21
