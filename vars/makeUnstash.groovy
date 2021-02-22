def call(String stashName, Boolean debug = false) {

    String debugPostfix = debug ? "Debug" : ""

    String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"
    
    String stdout

    try {
        withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "${CIS_TOOLS}/unstash${debugPostfix}.sh $REMOTE_HOST ${remotePath}")
            } else {
                stdout = bat(returnStdout: true, script: "%CIS_TOOLS%\\unstash${debugPostfix}.bat %REMOTE_HOST% ${remotePath}")
            }
        }

        if (debugPostfix) {
            println(stdout)
        }
    } catch (e) {
        println("Failed to unstash stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        throw e
    }

}
