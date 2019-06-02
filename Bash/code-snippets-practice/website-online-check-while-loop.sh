#!/bin/bash


function onlinecheck {
    while ping -c 1 google.com > /dev/null
    do
        echo "website is reachable ..."
        sleep 5;
    done
    offlinecheck
}

function offlinecheck {
    until ping -c 1 google.com > /dev/null
    do
        echo "website is NOT reachable ..."
        sleep 5;
    done
    onlinecheck
}

onlinecheck
