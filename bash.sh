#!/bin/bash
#!/path/to/interpreter
#!/bin/bash -e
uptime
df -h
top , htop
mfree

./script.sh
cat ~/.bash_profile
ps -ef | grep 'SC140'
/etc/init.d/

ls /not/here
echo "$?"
mv *.txt notes
grep 'test' test.txt
grep 'build job' -lR
grep bob /etc/passwd | cut -d ':' -f1,5 | sort | tr ":" " " | column -t


sed '/^\s*$/d' file.txt delete empty lines.

ssh-keygen -t rsa
cut -d ':' -f 2
awk -F "." '{print $1}'
awk -F ':' '{print $3 " -> " $1}'

find /etc -type f -iname yum*


export TZ="US/Central"
while read LINE
do
	echo "$LINE"
done < /etc/fstab

proj=$(ls -d devops* | cut -d '/' -f 1)
APPTYPE=$(echo $apptype | awk '{print toupper($0)}')

cp -r ./$1/* ./devops*/
sed -i "s/country/$cntry/g" $proj/$1/db/config/common-db.json
dbname=$(grep "^$cntry\s" reposlist.txt | grep "\s$apptype\s" | grep "\s$1\s" | awk '{print $5}')
[ ! -z "$dbname" ] && sed -i "s/DBNAME/$dbname/g" $proj/$1/db/config/common-db.json

case $envn in
	devcicd)
		;;
	nprd)
		;;
	prod)
		;;
	*)
		break
	;;
esac
read -p "Please Enter a Message: `echo $'\n> '`" message
read -p "Enter y or n :" ANSWER
case "$ANSWER" in
# start|START) stop|STOP)   [Yy]|[Yy][Ee][Ss])  [Yy]*)



MY_SHELL="bash"
if [ "MY_SHELL" = "bash" ]
then
elif [ condition ]
else
fi

for COLOR in red green blue
do
 echo "COLOR: $COLOR"
done


script.sh param1 param2 param3
# $0:"script.sh" $1:"param1" $2:"param2"
USER=$1
for USER in $@
for FILE in *.html
for FILE in /var/www/*.html

read -p "PROMPT" VARIABLE
read -p "Enter a user name: " USER

runit(){
./set-db-config.sh ;
}
runit


chmod 755 script.sh
rm -rf devops*
NUM_PARAMS="$#"
[ "$NUM_PARAMS" -eq "2" ] && REPOS=$(grep "^$1\s" reposlist.txt | grep "\s$2\s" | awk '{print $4}' | uniq);
echo -e "\n       === $reponame ===\n"

file1="common/common-pipeline.properties"
[ ! -e "$file1" ] && echo -e "\n       === FILE NOT THERE ===\n"
envn=$(echo "$reponame" | cut -d '-' -f 4)

echo "I am ${MY_SHELL}ing"
SERVER_NAME=$(hostname)
SERVER_NAME=`hostname`
first-three-letters="ABC" # invalid declaration

[ -e /etc/passwd ]
[ -d /etc/dir ]
--- string operators tests ----
[ -z $mystr ] True if string is empty
[ -n $mystr ] True if string is not empty
STRING1 = STRING2
STRING1 != STRING2
-------- Arithmetic operators-------
arg1 -eq arg2 #  -ne -lt -le -gt -ge

HOST="google.com"
ping -c 1 $HOST
if [ "$?" -eq "0" ]
then
else
fi
ping -c 1 $HOST && echo "$HOST is reachable"
ping -c 1 $HOST || echo "$HOST is NOT reachable"

logical && and  ||
mkdir /tmp/bak && cp test.txt /tmp/bak
cp test.txt /tmp/bak || cp test.txt /tmp

git add .; git commit -m "update"; git push

HOST="google.com"
ping -c 1 $HOST
if [ "$?" -eq "0" ]
then
	echo "$HOST unreachable"
	exit 1
fi
exit 0

---------
function hello(){
 echo "Hello : $1"
 for NAME in $@
 do
	echo "Hello $NAME"
 done
}
hello(){
}
hello jason Dan Ryan  # no need to mention ()
local LOCAL_VAR=1

---------
function backup_file(){
 if [ -f $1]
 then
	cp $1 $BACK
 else
	return 1
 fi
}
-------
backup_file /etc/hosts
if [ $? -eq 0]
then
	echo "Backup succeeded!"
fi
-------
basename $(pwd)

date +%F    # 2019-05-23
mi=$(date +%F).$$ ; echo $mi # 2019-05-23.2296  $$ is PID

----------
* matches zero or more char, ? matches exactly one char
*.txt, a*, a*.txt -- ?.txt,a?,a?.txt
[] A character class, [aeiou], ca[nt]*, [!aeiou]*
[a-g]*,[3-6]* ranges
\ escape character. to match a wildcard character.
*\? this matches done?
ls a?.txt matches ab.txt
----------

INDEX=1
while [ $INDEX -lt 6 ]
while true
do
	command N
	sleep 10
	((INDEX++))
done
while [ "$CORRECT != "y" ]
do
	read -p "Enter your name: " NAME
	read -p "Is ${NAME}" correct? " CORRECT
done
while ping -c 1 app1 >/dev/null
do
	echo "app1 still up ..."
	sleep 100
done
echo "app1 is down..."
while read LINE
do
 echo "$LINE"
done < /etc/fstab
grep xfs /etc/fstab | while read FS MP REST
grep xfs /etc/fstab | while read LINE
do
	echo "xfs:${LINE}"
done
mysql -BNe 'show databases' | while read DB
do
	db-backed-up-recently $DB
	if [ "$?" -eq "0" ]
	then
		continue
	fi
	backup $DB
done
-------debugging
-x prints commands as they execute
#!/bin/bash -x
set -x
set +x to stop debugging
-v prints shell input lines as they are read
-e Exit on error
#!/bin/bash -xe
#!/bin/bash -vx this is read & execute
help set | less
debug(){
	echo "Executing: $@"
	$@
}
debug ls
PS4='+ $BASH_SOURCE : $LINENO '
+ ./test.sh : TEST_VAT=test

echo "testing regex" | awk '/[Tt]esting regex/{print $2}'
awk '/[^oi]th/{print $0}' myfile
awk '/s[ae]*t/{print $0}'

echo $'\none \ntwo \nthree' # \n \t
