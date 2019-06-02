#!/bin/bash

# touch Hock123.txt
# echo " this 2563MB is 20% one" > Hock123.txt
# echo " this 256385MB is 80% one" >> Hock123.txt
# echo " this 89563MB is 87% one" >> Hock123.txt
# echo " this 58965MBis 90% one" >> Hock123.txt
# echo " this 65795MBis 99% one" >> Hock123.txt
# echo " this 869MB is 52% one" >> Hock123.txt

# egrep '\s[89][0-9]%\s' Hock123.txt | while read LINE
# do
#   echo "$LINE" | awk '{print $2}' | cut -d 'M' -f 1
# done

for LINE in "$(egrep '\s[89][0-9]%\s' Hock123.txt)"
do
  echo "$LINE" | awk '{print $2}' | cut -d 'M' -f 1
done