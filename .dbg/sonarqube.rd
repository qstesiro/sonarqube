# 编译
{
    # 8.6
    {
        export JAVA_HOME=/home/qstesiro/openjdk-14.0.1
        export PATH=$JAVA_HOME/bin:$JAVA_HOME/jre/bin:$PATH
    }

    # 10.1
    {
        export JAVA_HOME=/home/qstesiro/openjdk-17.0.1
        export PATH=$JAVA_HOME/bin:$JAVA_HOME/jre/bin:$PATH
    }

    alias clean='./gradlew clean --parallel --build-cache --console plain --exclude-task test'
    alias build='./gradlew build --parallel --build-cache --console plain --exclude-task test && rm -rf sonar-application/build/distributions/sonarqube-8.6.0-SNAPSHOT && unzip -d sonar-application/build/distributions/   sonar-application/build/distributions/sonar-application-8.6.0-SNAPSHOT.zip && rm -rf ./lib && cp -r  sonar-application/build/distributions/sonarqube-8.6.0-SNAPSHOT/lib ./lib'

    export SONAR_ELASTIC_HOST="es-cn-9g4mkz6a5c58utt8x.elasticsearch.aliyuncs.com"
    export SONAR_ELASTIC_PORT=9200
    export SONAR_ELASTIC_USER="elastic"
    export SONAR_ELASTIC_PASSWORD=""

    export SONAR_WORKER_COUNT="0"
    export SONAR_CORE_MULTIPLE="0"

    alias run='./sonar-application/build/distributions/sonarqube-8.6.0-SNAPSHOT/bin/linux-x86-64/sonar.sh console'

    alias log-ce='while [[ ! -f "sonarqube-8.6.0-SNAPSHOT/logs/ce.log" ]]; do sleep 1s; done; tail -f -n 10240 sonarqube-8.6.0-SNAPSHOT/logs/ce.log'
    alias log-es='while [[ ! -f "sonarqube-8.6.0-SNAPSHOT/logs/es.log" ]]; do sleep 1s; done; tail -f -n 10240 sonarqube-8.6.0-SNAPSHOT/logs/es.log'
    alias log-web='while [[ ! -f "sonarqube-8.6.0-SNAPSHOT/logs/web.log" ]]; do sleep 1s; done; tail -f -n 10240 sonarqube-8.6.0-SNAPSHOT/logs/web.log'
}

# 扫描
{
    # http://192.168.56.106:9000
    mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.6.0.1398:sonar \
        -Dsonar.host.url=http://localhost:9000 \
        -Dsonar.login=admin \
        -Dsonar.password=hmm \
        -Dsonar.projectKey=console:wuxing-cs \
        -Dsonar.projectName=console:wuxing-cs \
        -Dsonar.projectVersion=1 \
        -Dsonar.working.directory=.scannerwork \
        -Dmaven.test.skip=true \
        -pl business -am
}

# docker
{
    docker build ./ -t reg-qd-huangdao.xxx.net/library/sonarqube:8.6.0-community
    docker push reg-qd-huangdao.xxx.net/library/sonarqube:8.6.0-community
    docker pull reg-qd-huangdao.xxx.net/library/sonarqube:8.6.0-community
}
