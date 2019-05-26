#!/bin/bash

MAIN="Kamalakar"
LEN=$(echo ${#MAIN})
INDEX=1
REV_STR=""

while [ $INDEX -le $LEN ]
do
    #echo "$INDEX"
    ONE_CHAR=$(echo $MAIN | cut -c $INDEX)
    REV_STR="${ONE_CHAR}${REV_STR}"
    ((INDEX++))
done

echo "the reverse string is : $REV_STR"