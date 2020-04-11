def call(String repo)
{
    print('Try clone repo with assets')
    try{
        dir("${CIS_TOOLS}/../../TestResources/${repo}")
        {
            checkOutBranchOrScm('master', "https://gitlab.cts.luxoft.com/dtarasenko/${repo}.git", true)
        }
    } catch(e){
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());  
    }
}
