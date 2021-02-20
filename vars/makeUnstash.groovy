def call(String stashName, Boolean debug = false) {

    String debugPostfix = debug ? "Debug" : ""

    String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}"

    try {
        withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
            if (isUnix()) {
                status = sh(script: "${CIS_TOOLS}/unstash${debugPostfix}.sh $REMOTE_HOST \"${remotePath}\"")
            } else {
                status = bat(script: "%CIS_TOOLS%\\unstash${debugPostfix}.bat %REMOTE_HOST% \"${remotePath}\"")
            }
        }
    } catch (e) {
        println("Failed to unstash stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        throw e
    }

}
