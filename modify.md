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

- 支持插件装载配置
  - sonar-scanner-engine/src/main/java/org/sonar/scanner/bootstrap/PluginInstaller.java
  - sonar-scanner-engine/src/main/java/org/sonar/scanner/bootstrap/ScannerPluginInstaller.java

- 修改接口支持按类型查询扫描
  - server/sonar-db-dao/src/main/java/org/sonar/db/component/ComponentDto.java
  - server/sonar-db-dao/src/main/java/org/sonar/db/component/SnapshotQuery.java
  - server/sonar-db-dao/src/main/resources/org/sonar/db/component/SnapshotMapper.xml
  - server/sonar-webserver-webapi/src/main/java/org/sonar/server/projectanalysis/ws/ScanType.java
  - server/sonar-webserver-webapi/src/main/java/org/sonar/server/projectanalysis/ws/SearchAction.java
  - server/sonar-webserver-webapi/src/main/java/org/sonar/server/projectanalysis/ws/SearchData.java
  - server/sonar-webserver-webapi/src/main/java/org/sonar/server/projectanalysis/ws/SearchRequest.java
  - server/sonar-webserver-ws/src/main/java/org/sonar/server/ws/WebServiceEngine.java
  - sonar-ws/src/main/java/org/sonarqube/ws/client/projectanalyses/SearchRequest.java

- 遗留问题
  - 外置es可能存在升级问题,需要进一步调研
