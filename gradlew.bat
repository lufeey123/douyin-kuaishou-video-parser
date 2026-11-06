@echo off
setlocal
set "JAVA_CMD=java.exe"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -Dorg.gradle.appname=gradlew "-Dgradle.user.home=%~dp0.gradle-user-home" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
