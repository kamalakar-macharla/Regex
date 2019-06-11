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
docker container run -it   #start new container interactively ( -t allocate pseudo TTY, -i interactive)
docker container exec -it  #run additional command in existing container

No SSH Needed
docker cli is great substitude for adding SSH to containers

docker container run [OPTIONS] IMAGE [COMMANDS] [ARG...]

docker container run -it --name proxy nginx bash
docker container exec     #Run additional process in running container
docker container exec --help
docker container exec -it mysql bash

docker pull alpine
docker image ls

docker container port webhost
80/tcp -> 0.0.0.0:80
docker container inspect --format '{{ .NetworSettings.IPAddress}}' webhost
172.17.0.2
docker network ls
docker network create --driver  #create a network
docker network connect          #Attach a network to container
docker network disconnect

--------------------dockerfile--------------
FROM debian:stretch-slim
ENV NGINX_VERSION 1.13.6-1~stretch
RUN apt-get update \
	&& apt-get install --no-install-recommends --no-install-suggests -y gnupg1 \
	&& \
	NGINX_GPGKEY=573BFD6B3D8FBC641079A6ABABF5BD827BD9BF62; \
	found=''; \
RUN ln -sf /dev/stdout /var/log/nginx/access.log \
	&& ln -sf /dev/stderr /var/log/nginx/error.log
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
--------------------------------------------------------
docker image build -t customnginx .     # here . is current dir contains Dockerfile
										build happen in the layers wise which would be cached
vim dockerfile
build mayhappen using cached layers.

Extending offical Images.
FROM nginx:latest
WORKDIR /usr/share/nginx/html
COPY index.html index.html

docker image build -t nginx-with-html .
docker image tab --help
docker image tag nginx-with-html:latest bretfisher/nginx-with-html:latest
docker image ls

-------------------------------------------------
FROM node:6-alpine
EXPOSE 3000
RUN apk add --update tini
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY package.json package.json
RUN npm install && npm cache clean
COPY . .
CMD [ "tini", "--", "node", "./bin/www" ]
-----------
docker build -t testnode .
docker images
docker tag testnode bretfisher/testing-node
docker push --help
docker push bretfisher/testing-node
docker image ls
docker image rm bretfisher/testing-node
docker container run --rm -p 80:3000 bretfisher/testing-node

docker image prune     to clean up just "dangling" images 
docker system prune     will clean up everything
The big one is usually docker image prune -a which will remove all images youre not using. 
Use docker system df to see space usage.

--------persistent data-----------
containers are usually immutable and ephemeral
"immutable infrastructure": only re-deploy containers, never change
This is the ideal scenario, but what about databases, or unique data?
Docker gives us features to ensure these "separation of concerns"
This is known as persistent data
Two ways : data volumes and Bind Mounts
data volumes: make special location outside of container UFS (union file system)
Bind Mounts: link container path to host path

go to github.com/docker-library/mysql
and check Dockerfile, check VOLUME command
VOLUME /var/lib/mysql

docker pull mysql
docker image inspect mysql   then look for the volume info
docker container run -d --name mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=True mysql
docker container ls
docker container inspect mysql   # then check volume as well as Mounts info

docker volume ls
DRIVER    VOLUMENAME
local      946w149gw1g49158158

docker volume inspect 946w149gw1g49158158



