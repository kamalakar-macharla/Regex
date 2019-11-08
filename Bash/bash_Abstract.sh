office/personal work, anything doing regularly
or in bluk, want to reduce the regular doing work        -------> bash
Automate personal work to get more time.


find file,dirs based type
size, modify dates             ----> find                   # find ./ -type f -exec sed -i -e 's/XXXXX/kamalakar/g' {} \;


file1 file2 file3 file4 file5
file2														   # ls | xargs  makes the result into oneline(Horizontal lines to vertical)
file3                           ----> for | xargs | while      # for Loop works for Horizontal/ vertical items   
file4                                                          # xargs converts vertical items to horizontal items; echo -e "one \ntwo \nthree" | xargs
file5                                                          # while Loop takes vertical items as one line, horizontal items as one single line          


search file, over the files in dir            --------> grep
or output of command(ps, df -h, top) data 

OrderDate	Region	  Rep
1/6/2018	East	  Jones
1/23/2018	Central	  Kivell	     -----> awk
2/9/2018	Central	  Jardine
2/26/2018	Central	  Gill

daemon:x:2:2:daemon:/sbin:/sbin/nologin       ------> cut     # when you have one line, to get specific word



stream editor
Edit the files                      ---------> sed
search word & replace


read the lines directly from the file         ----> while command
read the lines from the grep result           ----> while $(grep -i 'new' file)
Loop it specific number of times              ----> while true
run continuously on condition of true/false   ----> while read LINE
continuously run on true till gets false      ----> echo "one two three four" | while read ITEM   # horizontal items consider as one line
can be used service or websites monitor       ----> echo -e "one \ntwo \nthree \nfour" | while read ITEM  # vertical items runs the loop

In this case,choice,scenarion
specific thig should done             --------> case

when automating some thing
provide input parameters              --------> read

if file or dir exists                 --------> []

logical and or or conditions          --------> &&  ||

make a decisions on exit code         --------> [ $? -eq 0 ]


sort , | column -t, uniqe,		# after sort , unique command works.