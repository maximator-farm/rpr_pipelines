def call(Map params) {

    String stashName

    try {
        stashName = params["name"]
        Boolean allowEmpty = params["allowEmpty"]
        String includes = params["includes"]
        String excludes = params["excludes"]
        Boolean debug = params["debug"]
        String debugPostfix = debug ? "Debug" : ""

        def sendParams = []

        // the greates importance has include/exclude which is the first!

        if (excludes) {
            for (exclude in excludes.split(",")) {
                sendParams << "--exclude '${exclude}'"
            }
        }

        if (includes) {
            for (include in includes.split(",")) {
                sendParams << "--include '${include}'"
            }
        } else {
            sendParams << "--include '**'"
        }

        sendParams << "--include='*/' --exclude='*'"

        sendParams = sendParams.join(" ")

        String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"

        String stdout

        withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, 
                    script: "${CIS_TOOLS}/stash${debugPostfix}.sh $REMOTE_HOST ${remotePath} \"${sendParams}\"")
            } else {
                stdout = bat(returnStdout: true, 
                    script: "%CIS_TOOLS%\\stash${debugPostfix}.bat %REMOTE_HOST% ${remotePath} \"${sendParams}\"")
            }
        }

        if (debugPostfix) {
            println(stdout)
        }

        if (stdout.contains("total size is 0 ") && !allowEmpty) {
            println("[ERROR] Stash is empty")
            throw new Exception("Empty stash")
        }
    } catch (e) {
        println("[ERROR] Failed to make stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        throw e
    }

}
