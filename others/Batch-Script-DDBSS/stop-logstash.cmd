@echo off

REM if the logstash is not running, print out the message and exit
tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="1" echo logstash is not running && exit /B 0

tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="0" taskkill /F /IM elk_java.exe && echo logstash has been stopped.

exit /B 0
