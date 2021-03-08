def call(String command)
{
    echo command
    String ret
    if(isUnix())
    {
        ret = sh(returnStdout: true, script:"python3 ${command}")
    }
    else
    {
        withEnv(["PATH+PYTHON_PATH=c:\\python35\\;c:\\python35\\scripts\\;"]) {
            ret = bat(
                script: "python ${command}",
                returnStdout: true
            )
        }
    }
    return ret
}
