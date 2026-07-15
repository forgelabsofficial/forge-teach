@echo off
REM Gradle wrapper
if "%JAVA_HOME%"=="" (
  echo WARNING: JAVA_HOME is not set. Gradle may not run properly.
)
set DIRNAME=%~dp0
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo The Gradle wrapper jar file is missing. Run 'gradle wrapper' on a machine with Gradle to bootstrap the wrapper, or download the distribution manually.
  exit /b 1
)
java -jar "%CLASSPATH%" %*
