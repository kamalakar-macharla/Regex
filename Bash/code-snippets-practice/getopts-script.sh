#!/bin/bash

usage(){
    echo "Usage: ${0} [-vs] [-l LENGTH]" >&2
    echo 'Generate a random password'
    echo '   -l  LENGTH specify the password length'
    echo '   -s         append a special charter'
    echo '   -v         verbose mode on'
    exit 1
}
log(){
    if [[ "${VERBOSE}" = 'true' ]]
    then
        echo "$@"
    fi
}
while getopts vl:s OPTION
do
    case ${OPTION} in
        v) VERBOSE=true; log 'Verbose Mode ON' ;;
        l) echo "Its l option" ; echo "arg is : ${OPTARG}" ;;
        s) echo "Its s option" ;;
        ?) usage ;;
    esac
done

log 'Generating a password'


#https://www.udemy.com/linux-shell-scripting-projects/learn/lecture/9439526#overview

