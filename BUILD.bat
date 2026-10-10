@echo off
setlocal
cd /d "%~dp0"
title Grim Storage Industries - building the jar
echo Building Grim Storage Industries. The first build downloads Minecraft and NeoForge and can take 5-20 minutes.
echo.
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Eclipse Temurin JDK 21 from https://adoptium.net
  echo and turn on "Set JAVA_HOME variable" in the installer, then run this again.
  pause
  exit /b 1
)
rem Ask Java where it really lives, so stale JAVA_HOME or org.gradle.java.home settings are ignored.
set "DETECTED_JAVA_HOME="
for /f "tokens=2 delims==" %%i in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home"') do set "DETECTED_JAVA_HOME=%%i"
for /f "tokens=*" %%a in ("%DETECTED_JAVA_HOME%") do set "DETECTED_JAVA_HOME=%%a"
if not exist "%DETECTED_JAVA_HOME%\bin\java.exe" (
  echo Could not work out where Java is installed. Send a screenshot of this window to Claude.
  pause
  exit /b 1
)
echo Using Java at: %DETECTED_JAVA_HOME%
set "JAVA_HOME=%DETECTED_JAVA_HOME%"
java -version
echo.
call gradlew.bat build "-Dorg.gradle.java.home=%DETECTED_JAVA_HOME%"
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the error lines above and send them to Claude.
  pause
  exit /b 1
)
echo.
echo BUILD SUCCESSFUL. Opening the folder with your jar: grimstorage-1.4.0.jar
explorer "%~dp0build\libs"
pause
