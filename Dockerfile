
FROM tomcat:10.1-jdk17-temurin

COPY target/football-manager.war /tmp/football-manager.war

RUN rm -rf /usr/local/tomcat/webapps/ROOT \
    && mkdir -p /usr/local/tomcat/webapps/ROOT \
    && cd /usr/local/tomcat/webapps/ROOT \
    && jar xf /tmp/football-manager.war \
    && rm /tmp/football-manager.war

EXPOSE 8080
