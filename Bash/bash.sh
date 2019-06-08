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
cat file | less 		# less is paging utility
cat file | head -2
cat file | tail -2
| tr ":" " " | column -t
Ctrl+R for history search
ls /not/here
echo "$?"
mv *.txt notes
grep options -i ignoring case, -c count, -n line num, -v invert match
grep 'test' test.txt
grep 'build job' -lR
grep bob /etc/passwd | cut -d ':' -f1,5 | sort | tr ":" " " | column -t
grep 'word1\|word2\|word3' /path/to/file

file filename 			# display the file type
strings binaryfile 		# to see text in the binary file
ls -d */   				# List directories only

sed '/^\s*$/d' file.txt delete empty lines.

ssh-keygen -t rsa
cut -d ':' -f 2  		# delimeter must be single character
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
read -p "Please Enter a Message: $(echo $'\n> ')" message
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

echo -e "Vone \nVtwo \nVthree \nVfour"
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

date +%F    				# 2019-05-23
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
awk --help | egrep '\-F'     # using \ as escaping char
awk --help | egrep '\-.'     # to see all the options staring with -anychar

echo $'\none \ntwo \nthree' # \n \t
echo $"\none \ntwo \nthree" # This doesnt work

MYVAR=file.txt
if [[ $MYVAR =~ .txt$ ]]
then
  echo "check..."
fi

for ITEM in $(echo "Red Green Yellow") 
for ITEM in $(echo $'Red \nGreen \nYellow') # for loop can take the Horizonal/vertical items
# for loop separate the items based on space between them
# if you want to combile them use "$(command)"
# while loop consider the entire line 

echo $'one \ntwo \nthree' | xargs
one two three
 echo "one two three" | xargs
one two three

echo $'one \ntwo \nthree' | xargs -n1
one
two
three

touch file{1..9}.jpg+*

grep -i 'angio' -lR
dev-qa-uat-prod.sh
multi-set-db-config.sh

grep -i 'angio' -lR | xargs
dev-qa-uat-prod.sh multi-set-db-config.sh

grep -i 'angio' -lR | xargs sed -i 's/angio/angio/Ig'

Special Character Classes [[:alpha:]] [[:alnum:]] [[:digit:]] [[:lower:]] [[:upper:]]

MAIN="Kamalakar"
echo ${#MAIN} # this gives length of a string
 ~ # tilda represent your home dir
 who<Tabcompletion> gives who whoami

while ping jsiehsu.com 
while command # if command exit status is zero, then while loop runs
until command # if command exit status is non-zero, then while loop runs

grep -i 'aig' -lR | xargs sed -i 's/aig/angio/Ig'  # DBS interview question, delete aig word in all files all dirs

DEBUG=true
$DEBUG && echo "Debug mode ON"

DEBUG=false
$DEBUG || echo "Debug mode OFF"

cat -A script.sh
cat -v script.sh # to see win CRLF/Carriage Return ^M
echo "Hello World"^M  #^M says windows line ending.
file script.sh # script.sh: Bourne-Again shell script, ASCII text executable, with CRLF line terminators
dos2unix script.sh  #converting file to Unix format...
file script.sh # script.sh: Bourne-Again shell script, ASCII text executable.

echo "one two three" | while read ITEM; do echo "The item is : $ITEM"; done
The item is : one two three
echo -e "one\ntwo\nthree" | while read ITEM; do echo "The item is : $ITEM"; done
The item is : one
The item is : two
The item is : three

ls > /dev/null
nwgpnpo 2> /dev/null
ls &> /dev/null       # for std error and output

crontab -l
crontab -e
crontab -r
30 08 10 06 * /home/maverick/full-backup
			30 – 30th Minute
			08 – 08 AM
			10 – 10th Day
			06 – 6th Month (June)
			* – Every day of the week

$#    Stores the number of command-line arguments that were passed to the shell program.
$?    Stores the exit value of the last command that was executed.
$0    Stores the first word of the entered command (the name of the shell program)
$*    Stores all the arguments that were entered on the command line ($1 $2 ...)
"$@"  Stores all the arguments that were entered on the command line, individually quoted ("$1" "$2" ...)