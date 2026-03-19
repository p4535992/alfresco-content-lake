@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%target\alfresco-content-syncer-1.0.0-SNAPSHOT-runner.jar
if not exist "%JAR%" (
  echo Runner jar not found: %JAR%
  echo Build first with: mvn -pl alfresco-content-syncer -am clean package
  exit /b 1
)
java -jar "%JAR%" %*
