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

    alias clean='./gradlew clean --console plain --exclude-task test'
    alias build='./gradlew build --console plain --exclude-task test && rm -rf sonar-application/build/distributions/sonarqube-8.6.1-SNAPSHOT && unzip -d sonar-application/build/distributions/   sonar-application/build/distributions/sonar-application-8.6.1-SNAPSHOT.zip'

    alias run='./sonar-application/build/distributions/sonarqube-8.6.1-SNAPSHOT/bin/linux-x86-64/sonar.sh console'

    alias log='while [[ ! -f "sonarqube-8.6.1-SNAPSHOT/logs/ce.log" ]]; do sleep 1s; done; tail -f -n 10240 sonarqube-8.6.1-SNAPSHOT/logs/ce.log'
}

# 扫描
{
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
