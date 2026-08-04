@echo off
setlocal
set "APP_HOME=%~dp0"

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME does not contain bin\java.exe: "%JAVA_HOME%" 1>&2
    exit /b 1
  )
) else (
  where java.exe >nul 2>&1
  if errorlevel 1 (
    echo ERROR: Java 21 was not found. Set JAVA_HOME or add java to PATH. 1>&2
    exit /b 1
  )
  set "JAVA_EXE=java.exe"
)

set "CC_AEROWORKS_PROJECT_DIR=%APP_HOME%"
"%JAVA_EXE%" "%APP_HOME%gradle\wrapper\GradleBootstrap.java" %*
exit /b %ERRORLEVEL%
