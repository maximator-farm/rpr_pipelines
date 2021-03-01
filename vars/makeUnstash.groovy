def call(String stashName, Boolean debug = false) {

    println("[INFO] Make unstash of stash with name '${stashName}'")

    String debugPostfix = debug ? "Debug" : ""

    String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"
    
    String stdout

    try {
        withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
            if (isUnix()) {
                sh(script: '$CIS_TOOLS/unstash.sh' + " ${remotePath} " + '$REMOTE_HOST')
            } else {
                bat(script: '%CIS_TOOLS%\\unstash.bat' + " ${remotePath} " + '$REMOTE_HOST')
            }
        }

        if (fileExists("stash_${stashName}.ziped")) {
            utils.renameFile(this, isUnix() ? "Windows" : "Unix", "stash_${stashName}.ziped", "stash_${stashName}.zip")
        } else if (fileExists("stash_${stashName}.zip")) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "unzip -u stash_${stashName}.zip")
            } else {
                stdout = bat(returnStdout: true, script: "bash -c \"unzip -u stash_${stashName}.zip\"")
            }
        } else {
            println("[ERROR] Could not find any suitable archive with stash content")
            throw new Exception("Could not find any suitable archive with stash content")
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
