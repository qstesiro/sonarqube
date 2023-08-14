from sonarqube:8.6.0-community

env SONAR_VERSION="8.6.0-SNAPSHOT"

run rm -rf /opt/sonarqube/lib
run mkdir -p /opt/sonarqube/lib

# workdir /opt/sonarqube
copy lib /opt/sonarqube/lib
