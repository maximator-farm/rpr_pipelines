@Field ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)

/**
 * Block which wraps processing of exceptions and notifications for them
 *
 * @param blockOptions Map with options (it's used for support optional params)
 * Possible elements:
 *     title - Name of status check which should be updated
 *     options - Options list
 *     buildUrl (optional) - Url which will be set in status check
 *     configuration - List with Map for each exception
 *     Possible elements:
 *         class - Class of target exception
 *         problemMessage - Displayed message
 *         rethrow - Describe should exception be rethrown and how (default - No)
 *         scope - scope of problem message (default - Specific)
 *         githubNotification - Map with values which describe params for updating status in Github repo
 *         Possible elements:
 *             status - Status which will be set
 *             message (optional) - New message for status check 
 * @param code Block of code which is executed
 */
def call(Map blockOptions, Closure code) {
    def title = blockOptions["title"]
    def options = blockOptions["options"]
    def buildUrl = blockOptions["buildUrl"]
    def configuration = blockOptions["configuration"]

    try {
        code()
    } catch (e) {
        // find necessary exception in configuration
        for (exception in configuration) {
            if ((exception["class"] == e.class) || (exception["class"] == "TimeoutExceeded" && utils.isTimeoutExceeded(e))) {
                switch(exception["rethrow"]) {
                    case ExceptionThrowType.RETHROW:
                        saveProblemMessage(options, exception, exception["problemMessage"], options["stage"], options["osName"])
                        throw e
                        break
                    case ExceptionThrowType.THROW_IN_WRAPPER:
                        throw new ExpectedExceptionWrapper(exception["problemMessage"], e)
                        break
                    default:
                        saveProblemMessage(options, exception, exception["problemMessage"], options["stage"], options["osName"])
                }
                if (exception["githubNotification"]) {
                    GithubNotificator.updateStatus(options["stage"], title, exception["githubNotification"]["status"], 
                        options, exception["githubNotification"]["message"] ?: "", buildUrl)
                }
                return
            }
        }
        // exception wasn't found in configuration. Rethrow it
        throw e
    }
}


def saveProblemMessage(Map options, Map exception, String message, String stage = "", String osName = "") {
    def messageFunciton
    // find function for specified scope
    switch(exception["scope"]) {
        case ProblemMessageManager.GENERAL:
            messageFunciton = problemMessageManager.&saveGeneralFailReason
            break
        case ProblemMessageManager.GLOBAL:
            messageFunciton = problemMessageManager.&saveGlobalFailReason
            break
        default:
            // SPECIFIC scope is default scope
            messageFunciton = problemMessageManager.&saveSpecificFailReason
    }
    if (osName) {
        messageFunciton(message, stage, osName) 
    } else if (stage) {
        messageFunciton(message, stage) 
    } else {
        messageFunciton(message) 
    }
}