@echo off
cd /d "%~dp0"
if not exist bin\classes mkdir bin\classes
set "SISCOM_LIBS=lib\gson.jar;lib\tomcat-embed-core.jar;lib\jbcrypt.jar;lib\h2.jar;lib\mysql-connector.jar;lib\javax-annotation.jar"
where javac >nul 2>nul
if errorlevel 1 (
 java -m jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -cp "%SISCOM_LIBS%" -d bin\classes src\main\java\pe\edu\siscom\*.java
) else (
 javac -source 8 -target 8 -encoding UTF-8 -cp "%SISCOM_LIBS%" -d bin\classes src\main\java\pe\edu\siscom\*.java
)
if errorlevel 1 exit /b 1
where jar >nul 2>nul
if errorlevel 1 (
 java -m jdk.jartool/sun.tools.jar.Main cf bin\siscom.jar -C bin\classes .
) else (
 jar cf bin\siscom.jar -C bin\classes .
)
if errorlevel 1 exit /b 1
echo Compilacion completada
