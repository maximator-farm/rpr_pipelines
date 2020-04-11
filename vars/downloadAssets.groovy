def call(String osName, String repo)
{
    print('Try clone repo with assets')
    try{
        switch(osName)
        {
            case 'Windows':
                dir("${CIS_TOOLS}/../../TestResources/$repo")
                {
                    checkOutBranchOrScm('master', "https://gitlab.cts.luxoft.com/dtarasenko/$repo\.git")
                }
            break;

            default:
                dir("/mnt/c/TestResources/$repo")
                {
                    checkOutBranchOrScm('master', "https://gitlab.cts.luxoft.com/dtarasenko/$repo\.git")
                }
        }
    } catch(e){
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());  
    }
}
