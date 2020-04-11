def call(String repo)
{
    dir("${CIS_TOOLS}/../TestResources/"){
        checkOutBranchOrScm(options['testsBranch'], "ssh://git@gitlab.cts.luxoft.com:30122/dtarasenko/{$repo}.git")
    }
}
