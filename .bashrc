export GITLABTOKEN="nRQcS8rqQym45u2zyC_e"
export DRTOKEN="GRS_PROD_secrete_token"
reposgitlab () {
 curl --header "PRIVATE-TOKEN: ${GITLABTOKEN}" https://git.platform.company-xyz.io/api/v4/groups/7366/ | jq .[] | jq .[] | jq .name | grep '^"' | sed 's/"//g' | sort > REST_api_Repos_list.txt
 curl --header "PRIVATE-TOKEN: ${GITLABTOKEN}" https://git.platform.company-xyz.io/api/v4/groups/1483/ | jq .[] | jq .[] | jq .name | grep '^"' | sed 's/"//g' | sort >> REST_api_Repos_list.txt
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
gc(){
 echo "https://git.platform.company-xyz.io/cdn-institutional-grs-devops-test/${1}.git"
 echo "https://git.platform.company-xyz.io/cdn-institutional-grs/${1}.git"
 echo "https://git.platform.company-xyz.io/gwam/cdn/${1}.git"
}
stjen(){
  start chrome https://jenkins.company-xyz.com/job/GRS_Projects/job/GRS_GRSPlus/
}
mkcd(){
  mkdir $1 ; cd $1
}
gpush(){
  git add .
  git commit -m 'update'
  git push
}
alias getrepos='reposgitlab'
alias npp='notepad++'
alias exp='explorer .'
alias getscript='cp -r ~/whileloop .'
alias winmerge='WinMergeU'
alias DR='cat ~/dr-script.txt'
alias hp='cat ~/hp.txt'
alias cdt='cd ~/temp'
