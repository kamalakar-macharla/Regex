@echo off

REM start logstash in a separate command window
echo Starting the logstash
start cmd /k logstash-agent.bat

REM waiting for 3 minutes to logstash to come up
timeout 180 > NUL

REM if imagename elk_java.exe not found, It means logstash not started successfully then exit with Non Zero value
tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="1" echo logstash is not started. Exiting with non zero value && exit /B 0

REM if logstash started successfully, control comes here then exit with zero value
echo Logstash started successfully && tasklist /FI "IMAGENAME eq elk_java.exe"
exit /B 0
