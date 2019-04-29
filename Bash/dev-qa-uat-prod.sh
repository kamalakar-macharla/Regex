#!/bin/bash -e
clear;
rm -rf devops*
##
BRANCHNAME="master"
NUM_PARAMS="$#"

[ "$NUM_PARAMS" -eq "1" ] && REPOS=$(grep "^$1\s" reposlist.txt | awk '{print $4}' | uniq);
[ "$NUM_PARAMS" -eq "2" ] && REPOS=$(grep "^$1\s" reposlist.txt | grep "\s$2\s" | awk '{print $4}' | uniq);
[ "$NUM_PARAMS" -eq "3" ] && REPOS=$(grep "^$1\s" reposlist.txt | grep "\s$2\s" | grep "\s$3\s" | awk '{print $4}' | uniq);
#[ $1 == "all" ] && REPOS=$(awk '{print $4}' reposlist.txt | grep -v '^$' | uniq);


clone_repo(){
	reponame="$1"
	echo -e "\n       === $reponame ===\n"
	git clone -b $BRANCHNAME "https://kmacharl@github.aig.net/commercial-it-config/${reponame}.git"
	
	common "$reponame";
	#dev_nprd_prod  "$reponame";
}

common(){
	reponame="$1"
	echo "from common function"
	cd $reponame
	file1="common/common-pipeline.properties"
	[ ! -e "$file1" ] && echo -e "\n       === FILE NOT THERE ===\n"
	#[ -e "$jfile1" ] &&
	#commit_changes "$reponame";
	cd ..
}

dev_nprd_prod(){
	reponame="$1"	
	envn=$(echo "$reponame" | cut -d '-' -f 4)	
	case $envn in
	devcicd)
		echo "its devcicd"
		;;
	nprd)
		echo "its nprd"
		;;
	prod)
		echo "its prod"
		;;
	esac
	
	sleep 2
	
	#commit_changes "$reponame";
}

commit_changes(){

	cd "$1";
	git add .;
	git status;
	#git commit -m "config changes in db configfiles" ;
	#git push ;
	cd .. ;
	#rm -rf devops*
	echo -e "     ========= END =========\n\n"
	sleep 2;

}

###
echo "$REPOS" |
while read -r line;
do
	clone_repo "$line";
	#common "$line";
	#dev_nprd_prod "$line";
	#[ "$?" -eq "0" ] && commit_changes "$line"
done



 