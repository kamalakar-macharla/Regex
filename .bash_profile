export GITLABTOKEN="nRQcS8rqQym45u2zyC_e"
export DRTOKEN="GRS_PROD_secrete_token"
reposgitlab () {
 curl --header "PRIVATE-TOKEN: ${GITLABTOKEN}" https://git.platform.mycompany.io/api/v4/groups/7366/ | jq .[] | jq .[] | jq .name | grep '^"' | sed 's/"//g' | sort > REST_api_Repos_list.txt
 curl --header "PRIVATE-TOKEN: ${GITLABTOKEN}" https://git.platform.mycompany.io/api/v4/groups/1483/ | jq .[] | jq .[] | jq .name | grep '^"' | sed 's/"//g' | sort >> REST_api_Repos_list.txt
 cat REST_api_Repos_list.txt > ~/repolist.txt
 # 1483
}
rs(){
 if [ $# -eq 0 ] ; then
    echo "rs search-word"
 fi
 if [ $# -eq 1 ] ; then
    grep -i "$1" ~/repolist.txt
 fi
}
mkcd(){
  mkdir $1 ; cd $1
}
gpush(){
  git add .; git commit -m 'update'; git push
}
# oneline jenkins
oneline(){
 if [ $# -eq 0 ] ; then
    cd ~/mastermind/; find -type f -iname '*-oneline.*' | grep -i "$1" | xargs -n1 basename
 fi
 if [ $# -eq 1 ] ; then
	cd ~/mastermind/; find -type f -iname '*-oneline.*' | grep -i "$1" | xargs notepad++
 fi
}
push(){
  cp ~/${1} ~/mastermind/
}
# echo 'copy to keyboard' | clip # copy out put to clip board
alias getrepos='reposgitlab'
alias npp='"/c/Program Files/Notepad++/notepad++.exe"'
alias npp='notepad++'
alias exp='explorer .'
alias getscript='cp -r ~/whileloop .'
alias winmerge='WinMergeU'
alias DR='cat ~/dr-script.txt'
alias hp='cat ~/hp.txt'
alias cdt='cd ~/temp'
alias jira='start chrome https://websitess.com/page'
alias pw='echo mypw@123 | clip' # to login into websites   jira;pw

# alias oneline='cd ~/mastermind/; find -type f -iname *-oneline.*'


