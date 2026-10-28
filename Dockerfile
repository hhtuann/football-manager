
FROM tomcat:10.1-jdk17-temurin

COPY target/football-manager.war /tmp/football-manager.war

RUN mkdir -p /usr/local/tomcat/webapps/football-manager \
    && cd /usr/local/tomcat/webapps/football-manager \
    && jar xf /tmp/football-manager.war \
    && rm /tmp/football-manager.war

EXPOSE 8080
