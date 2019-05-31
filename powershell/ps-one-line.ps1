#####################################################################################
xxx-yyyyy | Get-Member
xxx-yyyy  | select propertyname

Get-ChildItem | Get-Member
Get-ChildItem | select-string -Pattern "grep"


Get-ChildItem | select-string -Pattern "grep" | Get-Member
Get-ChildItem | select-string -Pattern "grep" | select filename
Get-ChildItem | select-string -Pattern "grep" | group filename

Get-ChildItem | select-string -Pattern "grep" | group filename | Get-Member
Get-ChildItem | select-string -Pattern "grep" | group filename | select name
grep -l 'grep' *.*

######################################################################################
ll | grep '.txt'
Get-ChildItem | Where-Object name -Like *.txt
####################################################

pwd
cd E:\GitHub-Repos\mastermind



Get-Command -verb enable
Get-Command | select -First 4 | select name
Get-Command | select source

Get-Command | Where-Object Name -Like *remoting


Get-Service | Where-Object {$_.Status -eq "Stopped"}
Get-Service | Where-Object status -Like *ing

Get-Service | Where-Object displayname -Like *Audio*

# To ps/cmd in spec dir - select folder url and type powershell , cmd
Get-Process | Select-Object -Property processname,cpu | Sort-Object -Property cpu | select -Last 5 > record.txt
Get-Service | Where-Object {$_.Status -eq "Stopped"}
Get-Service | Where-Object {$_.Name -eq "alg"} | Select-Object -Property displayname

pwd

Get-ChildItem -Path env:\ | Select-Object -Property name | Select-String -Pattern compu*

Get-ChildItem -path E:\GitHub-Repos\mastermind\powershell

Get-Process | Where-Object Path

Get-Help Where-Object -Online


Get-Process | Select-Object -Property processname,cpu | Sort-Object -Property cpu | select -Last 5 | Where-Object {$_.processname -eq "chrome"} | Get-Member

