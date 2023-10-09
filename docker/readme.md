#Build docker
docker build -t <<registry path>>/vertigo-analytics-server:<<version>> .

#tag version if not in build
#docker image tag <<image id>> <<registry path>>/vertigo-analytics-server:<<version>>

#login 
docker login --username <<Myuser_NAME>> --password <<harbor cli secret>> <<registry dns>>

#Push image
docker image push <<registry path>>/vertigo-analytics-server:<<version>>

