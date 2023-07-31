- 并发修改
  - server/sonar-process/src/main/java/org/sonar/process/ProcessProperties.java
  - server/sonar-ce/src/main/java/org/sonar/ce/configuration/CeConfigurationImpl.java
  - server/sonar-ce/src/main/java/org/sonar/ce/taskprocessor/CeWorkerImpl.java
  - server/sonar-ce-common/src/main/java/org/sonar/ce/configuration/WorkerCountProviderImpl.java
  - server/sonar-webserver-webapi/src/main/java/org/sonar/server/ce/ws/WorkerCountAction.java

- 外置es修改
  - server/sonar-process/src/main/java/org/sonar/process/ProcessProperties.java
  - server/sonar-application/src/main/java/org/sonar/application/App.java
  - server/sonar-main/main/java/org/sonar/application/SchedulerImpl.java
  - server/sonar-main/main/java/org/sonar/application/ProcessLauncher.java
  - server/sonar-main/main/java/org/sonar/application/ProcessLauncherImpl.java
  - server/sonar-server-common/src/main/java/org/sonar/server/es/EsClient.java
  - server/sonar-server-common/src/main/java/org/sonar/server/es/EsClientProvider.java

- 遗留问题
  - 外置es可能存在升级问题,需要进一步调研
