#####################################################################################
xxx-yyyyy | Get-Member
xxx-yyyy  | select propertyname

###########################################################################

Get-ChildItem | Select-String -Pattern 'grep' | group path | select name | ForEach-Object { 
   write-output $_.name
   Get-Content $_.Name
   sleep 5
   #Get-Content -Path $_
}


############################################################################

Get-ChildItem | Get-Member
Get-ChildItem | select-string -Pattern "grep"


Get-ChildItem | select-string -Pattern "grep" | Get-Member
cls
Write-Output $(Get-ChildItem | select-string -Pattern "grep" | group path | select name)

$myvar=$(Get-ChildItem | select-string -Pattern "grep" | group filename | select name)
cls
echo $myvar | Get-Content {$_ name}

Foreach ($i in $myvar){

 Get-Content .\$i

}
pwd
cd ..

cd .\test

Get-ChildItem | Get-Content
Get-ChildItem | select-string -Pattern "grep" | Where-Object filename | Get-Content

Get-ChildItem | select-string -Pattern "grep" | Where-Object filename | Get-Content

Get-ChildItem | select-string -Pattern "grep" | group filename | Where name -Match '*.txt' | Get-Content

Get-ChildItem | select-string -Pattern "grep" | group filename | where name -Match testingfile.txt

Get-ChildItem | select-string -Pattern "grep" | Get-Content
Get-ChildItem | select-string -Pattern "grep" | select filename | Get-Member
Get-ChildItem | select-string -Pattern "grep" | group filename

Get-ChildItem | select-string -Pattern "grep" | group filename | Get-Member
Get-ChildItem | select-string -Pattern "grep" | group filename | select name
grep -l 'grep' *.*

####################################################
ll | grep '.txt'
Get-ChildItem | Where-Object name -Like *.txt | select name

####################################################
GREP-ish
cat DATA.TXT | where { $_ -match "Mary"}
SED-ish
cat DATA.TXT | % { $_ -replace "Mary","Susan" }
Get-ChildItem .\test | Select-String -Pattern 'jiomee' | Get-Content | % { $_ -replace "jiomee","XXYYZZ" } | Set-Content .\xyz2.txt
####################################################
Get-Process | select processname                   # this return all objects
Get-Process | where processname -Match 'wmi*'      # this return match objects
##############################################


pwd
cd E:\GitHub-Repos\mastermind\powershell


cls
Get-ChildItem | Select-String -Pattern 'white' | Get-Member
Get-ChildItem | Select-String -Pattern 'white' | Select-Object filename 
Get-ChildItem | Select-String -Pattern 'white' | Select-Object filename | Get-Member

Get-ChildItem | Select-String -Pattern 'white' | Select-Object filename | Get-Content

Get-ChildItem | Select-String -Pattern 'white' | Select-Object filename | Get-Content  | % { $_ -replace "white","BLACK" }

Get-ChildItem | Select-String -Pattern 'white' | % { $_ -replace "white","BLACK" }

Get-Help Select-String -Online


Get-ChildItem | Select-String -Pattern 'white' | Select-Object -Last 1 | Get-Content

Get-ChildItem | Select-String -Pattern 'white' | Select-Object | % { $_ -replace "white","BLACK" }

Get-ChildItem | Select-String -Pattern 'white' | % { $_ -replace "white","BLACK" }

Get-ChildItem .\test | Select-String -Pattern 'jiomee' | Get-Content | % { $_ -replace "jiomee","XXYYZZ" } | Set-Content .\xyz2.txt

pwd
Get-ChildItem | 

cat .\textfile.txt | Where -Match
cat .\textfile.txt | where { $_ -match 'little' }

cat .\textfile.txt | %{ $_ -replace "little","litt-one"}

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

