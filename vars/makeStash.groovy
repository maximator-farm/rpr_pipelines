def call(Map params) {

    String stashName

    try {
        stashName = params["name"]

        println("[INFO] Make stash with name '${stashName}'")

        Boolean allowEmpty = params["allowEmpty"]
        String includes = params["includes"]
        String excludes = params["excludes"]
        Boolean debug = params["debug"]
        String debugPostfix = debug ? "Debug" : ""

        def zipParams = []

        if (includes) {
            for (include in includes.split(",")) {
                zipParams << "-i '${include}'"
            }
        }

        if (excludes) {
            for (exclude in excludes.split(",")) {
                zipParams << "-x '${exclude}'"
            }
        }

        zipParams = zipParams.join(" ")

        String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"

        String stdout

        String zipName = "stash_${stashName}.zip"

        if (!zipName.endsWith(".zip")) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "zip -r ${zipName} . ${zipParams}")
            } else {
                stdout = bat(returnStdout: true, script: "bash -c \"zip -r ${zipName} . ${zipParams}\"")
            }
        } else {
            utils.renameFile(this, isUnix() ? "Windows" : "Unix", zipName, "stash_${stashName}.ziped")
        }

        zipName = "stash_${stashName}.ziped"

        if (debug) {
            println(stdout)
        }

        if (stdout.contains("Nothing to do!") && !allowEmpty) {
            println("[ERROR] Stash is empty")
            throw new Exception("Empty stash")
        }

        withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
            if (isUnix()) {
                sh(script: '$CIS_TOOLS/stash.sh' + " ${zipName} ${remotePath} " + '$REMOTE_HOST')
            } else {
                bat(script: '%CIS_TOOLS%\\stash.bat' + " ${zipName} ${remotePath} " + '%REMOTE_HOST%')
            }
        }

    } catch (e) {
        println("[ERROR] Failed to make stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        throw e
    }

}
