#!/bin/bash
debug(){
	echo "Executing: $@"
	$@
}
debug ls
debug awk --help | egrep '\-.'
