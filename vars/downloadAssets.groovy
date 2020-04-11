def call(String repo)
{
    print('Try clone repo with assets#')
    try{
        if(isUnix())
        {
            dir("${CIS_TOOLS}/../../TestResources/")
            {
                checkOutBranchOrScm('master', "https://gitlab.cts.luxoft.com/dtarasenko/{$repo}.git")
            }
                //script: "${CIS_TOOLS}/receiveFilesSync.sh ${original_folder} ${CIS_TOOLS}/../TestResources/${destination_folder}")
        }
        else
        {
            dir("/mnt/c/TestResources/")
            {
                checkOutBranchOrScm('master', "https://gitlab.cts.luxoft.com/dtarasenko/{$repo}.git")
            }
        }
    } catch(e){
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());  
    }
}
