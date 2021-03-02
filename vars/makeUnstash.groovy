import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String stashName, Boolean debug = false) {

    String debugPostfix = debug ? "Debug" : ""

    String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"
    
    String stdout

    try {
        int times = 3
        int retries = 0
        int status = 0

        while (retries++ < times) {
            try {
                print("Try to make unstash №${retries}")
                withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
                    if (isUnix()) {
                        status = sh(script: '$CIS_TOOLS/unstash.sh' + " ${remotePath} " + '$REMOTE_HOST')
                    } else {
                        status = bat(script: '%CIS_TOOLS%\\unstash.bat' + " ${remotePath} " + '$REMOTE_HOST')
                    }
                }

                if (status != 24) {
                    break
                } else {
                    print("[ERROR] Partial transfer due to vanished source files")
                }
            } catch (FlowInterruptedException e1) {
                println("[INFO] Making of unstash of stash with name '${stashName}' was aborting.")
                throw e1
            } catch(e1) {
                println(e1.toString())
                println(e1.getMessage())
                println(e1.getStackTrace())
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
