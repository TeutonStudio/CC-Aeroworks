@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

where "%JAVA_EXE%" >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java 21 was not found. Set JAVA_HOME or add java to PATH. 1>&2
  exit /b 1
)

set "CC_AEROWORKS_PROJECT_DIR=%APP_HOME%"
"%JAVA_EXE%" "%APP_HOME%gradle\wrapper\GradleBootstrap.java" %*
exit /b %ERRORLEVEL%
