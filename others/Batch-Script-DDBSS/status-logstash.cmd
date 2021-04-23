@echo off
REM check if the logstash is running or not
tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="0" (echo logstash is running && tasklist /FI "IMAGENAME eq elk_java.exe") else (echo logstash is not working)

