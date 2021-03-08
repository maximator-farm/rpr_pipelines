import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String stashName, Boolean unzip = true, Boolean debug = false) {

    String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"
    
    String stdout

    try {
        int times = 3
        int retries = 0
        int status = 0

        String zipName = "stash_${stashName}.zip"

        while (retries++ < times) {
            try {
                print("Try to make unstash №${retries}")
                withCredentials([string(credentialsId: "nasURL", variable: "REMOTE_HOST")]) {
                    if (isUnix()) {
                        status = sh(returnStatus: true, script: '$CIS_TOOLS/unstash.sh' + " ${remotePath} " + '$REMOTE_HOST')
                    } else {
                        status = bat(returnStatus: true, script: '%CIS_TOOLS%\\unstash.bat' + " ${remotePath} " + '%REMOTE_HOST%')
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

        if (unzip) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "unzip -u ${zipName}")
            } else {
                stdout = bat(returnStdout: true, script: '%CIS_TOOLS%\\7-Zip\\7z.exe x' + " ${zipName}\"")
            }
        }

        if (debug) {
            println(stdout)
        }
        
        if (unzip) {
            if (isUnix()) {
                sh "rm -rf ${zipName}"
            } else {
                bat "del ${zipName}"
            }
        }

    } catch (e) {
        println("Failed to unstash stash with name '${stashName}'")
        println(e.toString())
        println(e.getMessage())
        throw e
    }

}
