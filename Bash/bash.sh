#!/bin/bash
#!/path/to/interpreter
#!/bin/bash -e
ssh-keygen -t rsa
ssh-copy-id root@RHost
hostname
whoami
who       #Print information about users who are currently logged in.
uptime
du -sh       # disk utilization -s smmary of grand total diskusage size.
df -h
top , htop
free -m | column -t
cat /etc/passwd       # all user names are avilable in this file
stat ./
service --status all
service jenkins status
service jenkins start

./script.sh
cat ~/.bash_profile
ps -ef | grep 'SC140'
/etc/init.d/            # find all running services here
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

mkdir -p dir1/dir2
rm -rf ./*/            # remove delete all dirs in current folder
rm -r dir1             #-r  remove directories and their contents recursively
rm -rf dir1/*

sed '/^\s*$/d' file.txt delete empty lines.

ssh-keygen -t rsa
cut -d ':' -f 2  		# delimeter must be single character
awk -F "." '{print $1}'
awk -F ':' '{print $3 " -> " $1}'

stat ./Bash/bash.sh    # displays accessed, changed, modified, birth, size etc details.

find /etc -type f -iname yum*
find /home -iname *.jpg
find / -type d -name Tecmint
find . -type f -name tecmint.p
find . -type f -name "*.php"
find ./ -mtime 30   				# file modified 30 days back.
find ./ -atime 30   				# file modified 30 days back.
find ./ -mtime +50 –mtime -100 		#more than 50 days back and less than 100 days.
find ./ -cmin -60 					#files which are changed in last 1 hour.
find ./ -mmin -60					#files which are modified in last 1 hour.
find ./ -amin -60					#files which are accessed in last 1 hour.
									Access - the last time the file was read
									Modify - the last time the file was modified (content has been modified)
									Change - the last time meta data of the file was changed (e.g. permissions)


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
	nprd | qa)
		;;
	prod)
		;;
	*)
		break
	;;
esac

nprd | qa | uat | --uat)  # you can use multiple patterns in case

read -p "Please Enter a Message: $(echo $'\n> ')" message
read -p "Enter y or n :" ANSWER
case "$ANSWER" in
# start|START) stop|STOP)   [Yy]|[Yy][Ee][Ss])  [Yy]*)

[ -f /etc/passwd ] && echo "File exist" || echo "File does not exist"

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
STRING1 = STRING2	the strings are equal
STRING1 != STRING2	the strings are not equal
INTEGER1 -eq INTEGER2	INTEGER1 is equal to INTEGER2
-------- Arithmetic operators-------
arg1 -eq arg2 #  -ne -lt -le -gt -ge

if [[ "${UID}" -eq 0 ]]        # double braket new wayto doit

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
	return 1      # in functions use return instead of exit
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
dirname /vagrant/file.txt
/vagrant

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
..code to debug...
set +x
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

rm -r ./dir    #-r, -R, --recursive   remove directories and their contents recursively. By default, rm does not remove directories.
echo "one two three" | while read ITEM; do echo "The item is : $ITEM"; done
The item is : one two three
echo -e "one\ntwo\nthree" | while read ITEM; do echo "The item is : $ITEM"; done
The item is : one
The item is : two
The item is : three

ls > /dev/null
nwgpnpo 2> /dev/null
ls &> /dev/null       # for std error and output

echo 'kamalakar' && \  # write single long command in multiple lines
echo 'aparna'


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

ps aux         #a = process all users, u = process's user/owner, x = processes not attached to a terminal

ifconfig  # ip address
ln -sf /dev/stdout /var/log/nginx/access.log
ln -sf /dev/stderr /var/log/nginx/error.log

ssh = secure shell
Network protocol used to connect to linux
No built in SSH client on windows
Git includes an SSH client

#!/bin/bash
INPUT_FILE=$1
BASE_DIR=$(dirname $INPUT_FILE)
BASE_NAME=$(basename -s .mp4 $INPUT_FILE)
TMP_FILE=$(mktemp --suffix=.wav)
OUT_FILE="$BASE_DIR/$BASE_NAME.wav"

ls [a-c]*.sh
ls [^a-c]*.sh
ls [Aa]*.sh
export myvar=lion

private ssh key gos to ~/.ssh

/bin
/dev/null, /dev/tty
/root           #Home directory for the root user.
/home           #Users home directories
/etc  
/opt
/lib
/tmp
/var/log
/usr/bin

sed '/^\s*$/d'  file.txt  #remove empty lines from file
find ./ -type -size +4096c   # to find files bigger than 4096 bytes
find ./ -type -size -4096c   # to find files smaller than 4096 bytes

export PATH=${PATH}:/sonar/bin
~/.bash_profile     #configuration file for configuring user environments. The users can modify the default settings and add any extra configurations in it.

touch {file1,file2}
rm {file1,file2}
touch file{1,2}
echo "$name_01"{3,6,9}

./command -yes -no /home/username
$# = 3
$* = -yes -no /home/username
$@ = array: {"-yes", "-no", "/home/username"}
$0 = ./command
$1 = -yes

yum -y update
yum -y install pkgname

rpm --nodeps -i xyz.rpm
rpm -ivh --nodeps xyz.rpm
rpm -q BitTorrent
rpm -qa | grep zlib-devel*

tar -zxf Python-2.7.6tgz install from source
cd Python-2.7.6
./configure
make
make install

/etc.repos.d
vi Yum-rhel5u5.repo gpgcheck=0

cat /etc/redhat-release
ln -s /opt/freeware/python2.6_64 Python


sudo
su
scp
rsync  # remote sync - copies and sync files to remote system
rsync -avz dailywork/   root@192.168.0.101/home

wget  www.jsig.com/down.pdf    
mfree
free -m
ps -aux | grep 'firefox'
ps -u root,kamal
kill -9 4956

chmod +x file.sh
chmod   # u+wx   go=rwx o-x u=rw,go=rw 777  file.txt
ll --block-size=M
ls -lash  # it prints sizes in human readable format

30 00 * * * /usr/sbin/reboot

ifconfig
netstat   #display connection info, routing table information etc
ping -c 5 www.tecmint.com
nslookup www.tecmint.com   #nslookup command also use to find out DNS related query. The following examples shows A Record (IP Address) of tecmint.com.
host www.google.com    #host command to find name to IP or IP to name in IPv4 or IPv6 and also query DNS records.
system-config-network     # GUI Tool to configure IP Address, Gateway, DNS etc

netstat -lntu
-l – prints only listening sockets
-n – shows port number
-t – enables listing of tcp ports
-u – enables listing of udp ports

/etc/sysconfig/network
A={1,2,3,4}
B={a,b,c,d}  # sets
read more info about array in linux

mount -l   #lists all mounts with LABELs

-rwxr-xr-x vagrant vagrant demo.sh  # first vagrant is username 2nd vagrant is groupname
chmod 755 demo.sh

7 = 4+2+1
    r+w+x
5 = 4+1
    r+x
	
type echo
echo is a shell builtin
help echo    # use help command for builtin commands
help if      # if is a shell builtin
help [[      # conditional command
help test    # evaluate conditional expression
$ type -a test
test is a shell builtin

man uptime   # use man for non-builtin 

A-WORD='hello'   #this is a invalid variable name
echo '$NAME'
$NAME            # single quotes prevents the expansion of variable, 
                 # if you want variables to be interpreted use double quotes
				 
id -n   #print a name instead of a number
id -u   #print only the effective user ID
id -u -n   #this gives the username
id -un	or whoami #whoami	#this gives the username

w   # show who is logged on and what they are doing
who  # show who is logged on
whoami  # show current logged in

chmod u=rwx file1.sh
chmod g+x  file1.sh
chmod u-x  file1.sh
chmod go+x file1.sh

cd ~  # change to user home dir like /home/vagrant

ls -a
ll -a  # show all files like .bash_profile

root UID always 0
sudo ./luser-demo02.sh
your UID is 0
your username is root
you are root
su
password:
#         #root user start with # all other start with $

if [ "$UID" -ne 0 ]
then
	echo "user is not root user"
	exit 1
fi
if [[ "$STR1" = "$STR2" ]]   #here we are comparing two strings, single = works here
exit 0;       #you may use this end of the shell file

su - jsmith
password:  

if [[ "${UID} -ne 0 ]]"
then
 echo 'please run with sudo or as root'
 exit 1
fi
echo "${RANDOM}"
man date
date +%s%N  # use it for random password generation

$ sha1sum udemy-urls.sh             # generate checksum number
191f3aa37ff6985beabf0aedd3a1dc0fae4ab537 *udemy-urls.sh

$ sha256sum udemy-urls.sh
22ea3c9fb17040ef0eaedbfb97dccac1f7363ff56cd976c84f770853eec24e3a *udemy-urls.sh

$ echo 'testing' | head -c2
te

date +%s%N | sha256sum | head -c8
echo 'testing' | fold -w1
echo 'testing' | fold -w1 | shuf
S=!@#$%^&*()_+=
echo "${S}" | fold -w1 | shuf | head -c1   #chossing one special character

sudo cp luser-dome06.sh /usr/local/bin/
which luser-dome06.sh

./luser-dome06.sh jason steve jan fred
how below thing works for the above line
for USER_NAME in "${@}"   # this consider as diff items
for USER_NAME in "${*}"   # this consider as one line

# The first parameter is the user name
USER_NAME="${1}"
# The rest of the parameters are for the account comments
shift
COMMENT="${@}"

useradd -m $USERNAME
if [[ "${?}" -ne 0 ]]
then
	echo 'The account could not be created'
	exit 1
fi

sudo passwd --stdin einstein < password  #changing the password for the user einstein

echo      # this gives empty line in the script
echo "something"

read X < /etc/centos-release
echo "$UID" > uid    # this command same as below one
echo "$UID" 1> uid   # makesure no space between 1 and >

head -n1 /file 
head -n1 /file /fakefile #STDOUT STDERR should be displayed on screen
head -n1 /file /fakefile > head.out  # STDOUT goes to file, STDERR to screen
head -n1 /file /fakefile 2> head.err  # STDERR goes to file, STDOUT to screen
head -n1 /file /fakefile > head.out 2> head.err
head -n1 /file /fakefile > head.out 2>> head.err
head -n1 /file /fakefile > head.both 2>&1  # this is old way of doing
head -n1 /file /fakefile &> head.both      # this is new way of doing
head -n1 /file /fakefile &>> head.both     # to append it same file

head -n1 /file /fakefile 2>&1 | cat -n
head -n1 /file /fakefile |& cat -n      #above and below gives same output

head -n1 /file /fakefile > /dev/null
head -n1 /file /fakefile 2> /dev/null
head -n1 /file /fakefile &> /dev/null

FD 0 - standard input
FD 1 - standard output
FD 2 - standard Error

case "${1}" in
	start) echo 'starting.' ;;
	stop) echo 'stoping.' ;;
	status) echo 'status' ;;
	*)
	echo 'supply a valid option'
	exit 1
	;;
esac

log(){
	local MESSAGE="${@}"
	echo "${MESSAGE}"
	logger -t "Reg: backup file" "${MESSAGE}"
}
log "Backing up ${FILE} to ${BACKUP_FILE}"
debug 'ls -l'
# Above log and debug functions are very useful, 
# you can use these two fun in different places in a shell file


logger -t my-script 'Tagging on'
sudo tail /var/log/messages

backup_file '/etc/passwd'
if [[ "$? -eq 0 " ]]
then
	log 'File backup succeeded!'  # here log is function see above
else
	log 'File backup failed'
	exit 1
fi

type -a getopts 
NUM=$((1+2))

netstat -nutl | grep -Ev '^Active|^Proto'   #Extended regular expression
netstat -nutl | grep ';'  #here goal is to get the data without header

netstat -nutl | grep ':' | awk '{print $4}' | awk -F ':' '{print $NF}'
su du /var
su du /var | sort -n      # n is for numeric sort
su du -h /var | sort -n
su du -h /var | sort -h   # h human readable format for sort command
sort -u                   # u is for uniq data
sort -n | uniq
uniq -c                 # how many occurence same thing has happen
sudo cat /var/log/messages | awk '{print $5}' | sort | uniq -c
sudo cat /var/log/messages | awk '{print $5}' | sort | uniq -c | sort -n
wc /etc/passwd
25 50 1245 /etc/passwd
wc -w /etc/passwd   50
wc -l /etc/passwd   25   #mostly we use this
grep bash /etc/passwd | wc -l    #how many users are using bash
grep -c bash /etc/passwd    # c option for occurence
cat /etc/passwd | sort -t ':' -k 3 -n    # -t field-separator -k sort via a key
sort -u  and uniq commands serve the same purpose

grep -v 'root' logfile
grep Failed syslog-sample | awk '{print $(NF - 3)}'
geoiplookup 182.100.67.59
echo "consider this data in two colums" | while read COUNT IP

for SERVER in $(cat ${SERVER_LIST})
do
done


usage(){
 echo "$1"; 
 shift; 
 shift;
 echo "$@";
}
usage first two three

# arrays
my_array=(foo bar)
my_array[0]=foo
echo ${my_array[1]}

$ for i in "${my_array[@]}"; do echo "$i"; done
foo
bar
$ for i in "${my_array[*]}"; do echo "$i"; done
foo bar
