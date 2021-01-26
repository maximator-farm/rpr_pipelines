/**
 * @param command
 * @return for Windows returned string contains executed command
 */
def call(String command, Boolean returnWindowsCommnad = true)
{
    echo command
    String ret
    if(isUnix())
    {
        ret = sh(returnStdout: true, script:"python3 ${command}")
    }
    else
    {
        // @ before command - it won't be return in output
        String returnCommnandSymbol = returnWindowsCommnad ? "" : "@"
        withEnv(["PATH=c:\\python35\\;c:\\python35\\scripts\\;${PATH}"]) {
            ret = bat(
                script: """
                ${returnCommnandSymbol}python ${command}
                """,
                returnStdout: true
            )
        }
    }
    return ret
}
