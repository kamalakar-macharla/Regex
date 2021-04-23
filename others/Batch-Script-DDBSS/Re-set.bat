@echo off

REM killing the filebeat if its running
tasklist /FI "IMAGENAME eq filebeat.exe" 2 > NUL | find /I /N "filebeat.exe">NUL
if "%ERRORLEVEL%"=="0" taskkill /F /IM filebeat.exe

REM killing the logstash if its running
tasklist /FI "IMAGENAME eq elk_java.exe" 2 > NUL | find /I /N "elk_java.exe">NUL
if "%ERRORLEVEL%"=="0" taskkill /F /IM elk_java.exe

REM remove/delete the data folder from filebeat registry
rmdir /s /q .\data

REM taking a backup of previous test results before every re-set
move D:\elk\logstash7.x\output\*.* move D:\elk\logstash7.x\output\results-store
echo ""
echo ""
echo "     RESET DONE SUCCESSFULLY    "
timeout 3 > NUL

