/**
 * @param command
 * @return for Windows returned string contains executed command
 */
def call(String command)
{
    println(command)
    String ret
    if (isUnix()) {
        ret = sh(returnStdout: true, script:"python3 ${command}")
    } else {
        withEnv(["PATH=c:\\python35\\;c:\\python35\\scripts\\;${PATH}"]) {
            ret = bat(
                script: """
                python ${command}
                """,
                returnStdout: true
            )
        }
    }
    return ret
}
