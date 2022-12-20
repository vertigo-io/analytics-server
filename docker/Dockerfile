FROM eclipse-temurin:11-jre-alpine
RUN apk add --no-cache bash
COPY ./bin/vertigo-analytics-server-0.9.1-jar-with-dependencies.jar /usr/app/bin/
COPY ./bin/wait-for-it.sh /usr/app/bin/
COPY ./bin/start.sh /usr/app/bin/
COPY ./conf/log4j2.xml /usr/app/conf/
RUN chmod +x /usr/app/bin/*.sh

EXPOSE 4562
ENTRYPOINT [ "/usr/app/bin/start.sh" ]
