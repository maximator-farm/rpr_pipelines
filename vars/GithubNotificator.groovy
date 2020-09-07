import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class which manages status checks of PRs
 */
public class GithubNotificator {

    def context
    def pullRequest
    List buildCases = []
    List testCases = []
    // this variable is used for prevent multiple closing of status checks in multi thread logic of pipeline
    AtomicBoolean statusesClosed = new AtomicBoolean(false)

    /**
     * Main constructor
     *
     * @param context
     * @param pullRequest Object of PR
     */
    GithubNotificator(context, pullRequest) {
        this.context = context
        this.pullRequest = pullRequest
    }

    /**
     * Function for init status checks of PR: create checks with pending status for all steps which will be executed
     *
     * @param options Options map
     * @param url Build url
     */
    def initPR(Map options, String url) {
        try {
            context.println("[INFO] Started initialization of PR notifications")

            options.platforms.split(';').each() {
                if (it) {
                    List tokens = it.tokenize(':')
                    String osName = tokens.get(0)
                    buildCases << osName

                    String gpuNames = ""
                    if (tokens.size() > 1) {
                        gpuNames = tokens.get(1)
                    }

                    if(gpuNames) {
                        gpuNames.split(',').each() { asicName ->
                            testCases << "${asicName}-${osName}"
                        }
                    }
                }
            }

            String statusTitle = ""
            statusTitle = "[PREBUILD] Version increment"
            pullRequest.createStatus("pending", statusTitle, "Status checks initialized", url)
            for (buildCase in buildCases) {
                statusTitle = "[BUILD] ${buildCase}"
                pullRequest.createStatus("pending", statusTitle, "This stage will be executed later...", url)
            }

            for (testCase in testCases) {
                statusTitle = "[TEST] ${testCase}"
                pullRequest.createStatus("pending", statusTitle, "This stage will be executed later...", url)
            }
            statusTitle = "[DEPLOY] Building test report"
            pullRequest.createStatus("pending", statusTitle, "This stage will be executed later...", url)
            context.println("[INFO] Finished initialization of PR notifications")
        } catch (e) {
            context.println("[ERROR] Failed to initialize PR notifications")
            context.println(e.toString())
            context.println(e.getMessage())
        }

    }

    /**
     * Function for update existing status check
     * Error status check can be replaced only by other error status check
     *
     * @param stageName Name of stage
     * @param title Name of status check which should be updated
     * @param status New status of status check
     * @param env Environment variables of main pipeline. It's used for decide that is it PR or not
     * @param options Options map
     * @param message Message of status check. If it's empty current message will be gotten
     * @param url Url of status check. If it's empty current url will be gotten
     */
    static def updateStatus(String stageName, String title, String status, env, Map options, String message = "", String url = "") {
        if (env.CHANGE_URL && options.githubNotificator) {
            options.githubNotificator.updateStatusPr(stageName, title, status, message, url)
        }
    }

    private def updateStatusPr(String stageName, String title, String status, String message = "", String url = "") {
        String statusTitle = "[${stageName.toUpperCase()}] ${title}"
        // remove testname from title if it's required
        if (stageName == 'Test') {
            if (statusTitle.count('-') >= 2) {
                String[] statusTitleParts = title.split('-')
                statusTitle = (statusTitleParts as List).subList(0, 2).join('-')
            }
        }
        try {
            for (prStatus in pullRequest.statuses) {
                if (statusTitle == prStatus.context) {
                    if (!url) {
                        url = prStatus.targetUrl
                    }
                    if (!message) {
                        message = prStatus.description
                    }
                    break
                }
            }

            pullRequest.createStatus(status, statusTitle, message, url)
        } catch (e) {
            context.println("[ERROR] Failed to update status for ${statusTitle}")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for receive current status of existing status check
     *
     * @param stageName Name of stage
     * @param title Name of status check which should be updated
     * @param env Environment variables of main pipeline. It's used for decide that is it PR or not
     * @param options Options map
     */
    static def getCurrentStatus(String stageName, String title, env, Map options) {
        if (env.CHANGE_URL && options.githubNotificator) {
            options.githubNotificator.getCurrentStatusPr(stageName, title)
        }
    }

    private def getCurrentStatusPr(String stageName, String title) {
        String statusTitle = "[${stageName.toUpperCase()}] ${title}"
        try {
            for (prStatus in pullRequest.statuses) {
                if (statusTitle == prStatus.context) {
                    return prStatus.state
                }
            }
        } catch (e) {
            context.println("[ERROR] Failed to get status for ${statusTitle}")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for close unfinished stages (pending or failure status checks)
     * All pending and failure status checks will be marked as errored
     * It can be useful if build was aborted
     *
     * @param env Environment variables of main pipeline. It's used for decide that is it PR or not
     * @param options Options map
     * @param message Message of status check. If it's empty current message will be gotten
     */
    static def closeUnfinishedSteps(env, Map options, String message = "") {
        if (env.CHANGE_URL && options.githubNotificator) {
            options.githubNotificator.closeUnfinishedStepsPr(message)
        }
    }

    private def closeUnfinishedStepsPr(String message = "") {
        try {
            if(statusesClosed.compareAndSet(false, true)) {
                //FIXME: get only first stages with each name (it's github API issue: check can't be deleted or updated)
                List stagesList = []
                stagesList << "[PREBUILD] Version increment"
                buildCases.each { stagesList << "[BUILD] " + it }
                testCases.each { stagesList << "[TEST] " + it }
                stagesList << "[DEPLOY] Building test report"
                for (prStatus in pullRequest.statuses) {
                    if (stagesList.contains(prStatus.context)) {
                        if (prStatus.state == "pending" || prStatus.state == "failure") {
                            if (!message) {
                                message = prStatus.description
                            }
                            pullRequest.createStatus("error", prStatus.context, message, prStatus.url)
                        }
                        stagesList.remove(prStatus.context)
                    }
                    if (stagesList.size() == 0) {
                        break
                    }
                }
                context.println("[INFO] Unfinished steps were closed")
            }
        } catch (e) {
            context.println("[ERROR] Failed to close unfinished steps")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for close status checks which represents test stages on some OS
     * It can be useful if building on some OS didn't finish successfully
     *
     * @param env Environment variables of main pipeline. It's used for decide that is it PR or not
     * @param options Options map
     * @param osName OS name on which building failed
     */
    static def failPluginBuilding(env, Map options, String osName) {
        if (env.CHANGE_URL && options.githubNotificator) {
            options.githubNotificator.failPluginBuildingPr(osName)
        }
    }

    private def failPluginBuildingPr(String osName) {
        try {
            for (prStatus in pullRequest.statuses) {
                if (prStatus.context.contains("[TEST]") && prStatus.context.contains(osName)) {
                    pullRequest.createStatus("error", prStatus.context, "Building stage was failed", prStatus.url)
                }
            }
            context.println("[INFO] Test steps were closed")
        } catch (e) {
            context.println("[ERROR] Failed to close test steps")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

}
