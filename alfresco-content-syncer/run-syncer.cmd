@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%target\alfresco-content-syncer-1.0.0-SNAPSHOT-runner.jar
set CONFIG_DIR=%SCRIPT_DIR%config
set CONFIG_FILE=%CONFIG_DIR%\application.properties
if not exist "%JAR%" (
  echo Runner jar not found: %JAR%
  echo Build first with: mvn -pl alfresco-content-syncer -am clean package
  exit /b 1
)
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"
if not exist "%CONFIG_FILE%" type nul > "%CONFIG_FILE%"
java -Dquarkus.config.locations="%CONFIG_FILE%" -jar "%JAR%" %*
