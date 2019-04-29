#!/bin/bash -e


runit(){

./set-db-config.sh ;
echo "==committing changes to Repo"
cd devops* ;
git add . ;
git status
git commit -m "added DB config files" ;
git push ;
sleep 2
cd .. ;
rm -rf devops*

}

git clone -b kmacharl_1 https://kmacharl@github.aig.net/commercial-it-config/devops-de-aegis-prod-2951.git
runit
sleep 5

# git clone -b kmacharl_1 https://kmacharl@github.aig.net/commercial-it-config/devops-it-aegis-nprd-2956.git
# runit
# sleep 5

# git clone -b kmacharl_1 https://kmacharl@github.aig.net/commercial-it-config/devops-it-aegis-prod-2956.git
# runit
# sleep 5