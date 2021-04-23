@echo off

REM loading Env's from env config file
CALL SetEnvs.cmd

REM killing the logstash if its already running
tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="0" taskkill /F /IM elk_java.exe

REM sleep for 3 seconds
timeout 3 > NUL

title logstashrun

REM assing the conf file full path to variable
set CONFPATH=%~dp0sre-log.conf
echo %CONFPATH%

REM changing the dir to logstash agent installation
cd /d %LOGSTASH_INSTALL_PATH%

REM starting the logstash with required input .conf file
.\bin\logstash.bat -f %CONFPATH%


