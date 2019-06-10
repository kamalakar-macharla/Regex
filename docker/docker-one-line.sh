docker is to package app & deploy to any env
Docker ‘write once, and execute anywhere’, Java promises the exact same thing with JVM

https://github.com/BretFisher/udemy-docker-mastery

docker for linux setup https://www.udemy.com/docker-mastery/learn/lecture/7742916?start=1140#overview
store.docker.com has instructions for each distro
curl -sSL https://get.docker.com/ | sh
curl -fsSL get.docker.com -o get-docker.sh
sh get-docker.sh
sudo usermod -aG docker bret
sudo docker version
client:
	version: 17.06
Server:                         server also called engine
	version: 17.06
Next step is to installing docke machine, and compose from github.com
https://github.com/docker/machine
https://github.com/docker/compose

docker info
docker        then enter, this shows all list of commands
docker run
docker container run

docker container run --publish 80:80 nginx     #then go browser and local host
docker container ls
CONTAINER-ID	IMAGE	COMMAND		CREATED		STATUS			PROTS					NAMES
6902565595		nginx	"nginx -g"	36 sec ago	35 seconds		0.0.0.0:80/tcp,			

docker container stop 690

docker info
docker        then enter, this shows all list of commands
docker run
docker container run

docker container run --publish 80:80 nginx     #then go browser and local host
docker container ls
CONTAINER-ID	IMAGE	COMMAND		CREATED		STATUS			PROTS					NAMES
6902565595		nginx	"nginx -g"	36 sec ago	35 seconds		0.0.0.0:80/tcp,			

docker container stop 690
docker container run --publish 808:80 --detach --name webhost nginx
docker container logs webhost
docker container top webhost
docker container --help
docker container ls -a
docker container rm 65g sin5 iso7

docker container run --publish 8080:80 --name webhost -d nginx:1.11 nginx -T
server -> Host-OS -> Hypervisor    -> Guest-OS -> [App+Libs]
server -> Host-OS -> Docker Engine ->  -> [App+Libs]
docker run --name mongo -d mongo
docker top mongo     #shows process info runing inside container
docker ps
-----------------------------------------------------
docker container run -d -p 3306:3306 --name db -e MYSQL_RANDOM_ROOT_PASSWORD=yes mysql
docker container logs db    #to check random password

docker container run -d --name webserver -p 8080:80 httpd
docker container ps            #this is similar to docker container ls

docker container run -d --name proxy -p 80:80 nginx
docker ps

curl localhost
curl localhost:8080
docker container stop sihgwi ihsw ghw0hg
docker ps -a
docker container ls -a # these are same

docker image ls   # shows all the images available in cash
REPOSITORY		TAG		IMAGE-ID	CREATED 	SIZE
httpd           latest  499189fwib  2weeksago   176 MB
mysql           latest  4856sienhs  2weeksago   406 MB
nginx           latest  8965sjueus  3weeksago   182 MB

----------------------------------------------
docker container top mysql       # process list in one container
docker container inspect mysql   # info of how the container started full json array
docker container stat mysql      # all the live info like cpu usage
----------------------------------------------------