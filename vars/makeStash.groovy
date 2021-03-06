import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Map params) {

    String stashName

    try {
        stashName = params["name"]

        Boolean allowEmpty = params["allowEmpty"]
        String includes = params["includes"]
        String excludes = params["excludes"]
        Boolean debug = params["debug"]
        Boolean zip = params.containsKey("zip") ? params["zip"] : true

        def includeParams = []
        def excludeParams = []

        if (zip) {
            if (includes) {
                for (include in includes.split(",")) {
                    if (isUnix()) {
                        includeParams << "-i '${include.trim()}'"
                    } else {
                        includeParams << "${include.replace('/**/', '/').replace('**/', '').replace('/**', '').trim()}"
                    }
                }
            }

            if (excludes) {
                for (exclude in excludes.split(",")) {
                    if (isUnix()) {
                        excludeParams << "-x '${exclude.trim()}'"
                    } else {
                        excludeParams << "-xr!${exclude.replace('/**/', '/').replace('**/', '').replace('/**', '').trim()}"
                    }
                    
                }
            }
        }

        includeParams = includeParams.join(" ")
        excludeParams = excludeParams.join(" ")

        String remotePath = "/volume1/Stashes/${env.JOB_NAME}/${env.BUILD_NUMBER}/${stashName}/"

        String stdout

        String zipName = "stash_${stashName}.zip"

        if (zip) {
            if (isUnix()) {
                stdout = sh(returnStdout: true, script: "zip -r ${zipName} . ${includeParams} ${excludeParams}")
            } else {
                stdout = bat(returnStdout: true, script: '%CIS_TOOLS%\\7-Zip\\7z.exe a' + " stash_${stashName}.zip ${includeParams ?: '.'} ${excludeParams}")
            }
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
                        if (zip) {
                            status = sh(returnStatus: true, script: '$CIS_TOOLS/stash.sh' + " ${zipName} ${remotePath} " + '$REMOTE_HOST')
                        } else {
                            status = sh(returnStatus: true, script: '$CIS_TOOLS/stash.sh' + " ${includes} ${remotePath} " + '$REMOTE_HOST')
                        }
                    } else {
                        if (zip) {
                            status = bat(returnStatus: true, script: '%CIS_TOOLS%\\stash.bat' + " ${zipName} ${remotePath} " + '%REMOTE_HOST%')
                        } else {
                            status = bat(returnStatus: true, script: '%CIS_TOOLS%\\stash.bat' + " ${includes} ${remotePath} " + '%REMOTE_HOST%')
                        }
                    }
                }

                if (status != 24) {
                    break
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
        
        if (zip) {
            if (isUnix()) {
                sh "rm -rf ${zipName}"
            } else {
                bat "del ${zipName}"
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
