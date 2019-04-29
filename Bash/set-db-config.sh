#!/bin/bash -e
clear
proj=$(ls -d devops* | cut -d '/' -f 1)
cntry=$(ls -d devops* | cut -d '-' -f 2)
CNTRY=$(echo $cntry | awk '{print toupper($0)}')
apptype=$(ls -d devops* | cut -d '-' -f 3)
APPTYPE=$(echo $apptype | awk '{print toupper($0)}')
envn=$(ls -d devops* | cut -d '-' -f 4)
gid=$(ls -d devops* | cut -d '-' -f 5 | cut -c -4)
#echo $apptype

loadtemplate(){
	cp -r ./$1/* ./devops*/
}
setconfig(){
	sed -i "s/country/$cntry/g" $proj/$1/db/config/common-db.json
	sed -i "s/COUNTRY/$CNTRY/g" $proj/$1/db/config/common-db.json
	sed -i "s/s3type/$apptype/g" $proj/$1/db/config/common-db.json
	sed -i "s/S3TYPE/$APPTYPE/g" $proj/$1/db/config/common-db.json
	sed -i "s/9999/$gid/g" $proj/$1/db/config/common-db.json
	sed -i "s/envtype/nprd/g" $proj/$1/db/config/common-db.json
	sed -i "s/subenv/$1/g" $proj/$1/db/config/common-db.json
	
	sed -i "s/COUNTRY/$CNTRY/g" $proj/common/common-pipeline.properties
	sed -i "s/S3TYPE/$APPTYPE/g" $proj/common/common-pipeline.properties
	sed -i "s/9999/$gid/g" $proj/common/common-pipeline.properties
	
	dbname=$(grep "^$cntry\s" reposlist.txt | grep "\s$apptype\s" | grep "\s$1\s" | awk '{print $5}')
	[ ! -z "$dbname" ] && sed -i "s/DBNAME/$dbname/g" $proj/$1/db/config/common-db.json
	cat "$proj/$1/db/config/common-db.json"
}

case $envn in
	devcicd)
		echo "its devcicd"
		loadtemplate devcicd
		setconfig dev
		
		sed -i "s/country/$cntry/g" $proj/dev/db/config/branch.properties
		sed -i "s/s3type/$apptype/g" $proj/dev/db/config/branch.properties
		sed -i "s/9999/$gid/g" $proj/dev/db/config/branch.properties

		;;
	nprd)
		loadtemplate nprd
		setconfig qa
		setconfig uat
		echo "its nprd"
		;;
	prod)
		loadtemplate prod
		setconfig prod
		sed -i "s/nprd/prod/g" $proj/prod/db/config/common-db.json
		cat "$proj/prod/db/config/common-db.json"
		echo "its prod"
		;;
esac