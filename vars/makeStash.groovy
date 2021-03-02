import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Map params) {

    String stashName

    try {
        stashName = params["name"]

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

        String remoteZipName

        if (includes?.split(",") == 1 && includes?.endsWith(".zip")) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "zip -r ${zipName} . ${zipParams}")
            } else {
                stdout = bat(returnStdout: true, script: "bash -c \"zip -r ${zipName} . ${zipParams}\"")
            }

            remoteZipName = zipName
        } else {
            remoteZipName = "stash_${stashName}.ziped"
        }

        if (debug) {
            println(stdout)
        }

        if (stdout?.contains("Nothing to do!") && !allowEmpty) {
            println("[ERROR] Stash is empty")
            throw new Exception("Empty stash")
        }

        int times = 3
        int retries = 0
        int status = 0

        while (retries++ < times) {
            try {
                print("Try to make stash №${retries}")
                withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
                    if (isUnix()) {
                        status = sh(returnStatus: true, script: '$CIS_TOOLS/stash.sh' + " ${zipName} ${remotePath}/${remoteZipName} " + '$REMOTE_HOST')
                    } else {
                        status = bat(returnStatus: true, script: '%CIS_TOOLS%\\stash.bat' + " ${zipName} ${remotePath}/${remoteZipName} " + '%REMOTE_HOST%')
                    }
                }

                if (status != 24) {
                    return
                } else {
                    print("[ERROR] Partial transfer due to vanished source files")
                }
            } catch (FlowInterruptedException e1) {
                println("[INFO] Making of stash with name '${stashName}' was aborting.")
                throw e1
            } catch(e1) {
                println(e1.toString())
                println(e1.getMessage())
                println(e1.getStackTrace())
            }
        }

    } catch (e) {
        println("[ERROR] Failed to make stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        println(e.getStackTrace())
        throw e
    }

}
