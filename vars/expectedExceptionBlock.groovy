/**
 * Block which wraps processing of exceptions and notifications for them
 *
 * @param blockOptions Map with options (it's used for support optional params)
 * Possible elements:
 *     title - Name of status check which should be updated
 *     osName (optional) - Name of OS (if it's possible that there are few different OS for same stage)
 *     env - Environment variables
 *     options - Options list
 *     buildUrl (optional) - Url which will be set in status check
 *     configuration - List with Map for each exception
 *     Possible elements:
 *         class - Class of target exception
 *         problemMessage - Displayed message
 *         rethrow - Describe should exception be rethrown and how (default - No)
 *         scope - scope of problem message (default - Specific)
 *         enableGithubNotification - Update status in Github repo or not
 *         githubNotification - Map with values which describe params for updating status in Github repo
 *         Possible elements:
 *             status - Status which will be set
 *             message (optional) - New message for status check 
 * @param code Block of code which is executed
 */
def call(Map blockOptions, Closure code) {
    try {
        code()
    } catch (e) {
        // find necessary exception in configuration
        for (exception in blockOptions["configuration"]) {
            if ((exception["class"] == e.class) || (exception["class"] == "TimeoutExceeded" && utils.isTimeoutExceeded(e))) {
                switch(exception["rethrow"]) {
                    case ExpectedExecutionThrowType.RETHROW:
                        saveProblemMessage(exception, exception["problemMessage"], stage, osName)
                        throw e
                        break
                    case ExpectedExecutionThrowType.THROW_IN_WRAPPER:
                        throw new ExpectedExceptionWrapper(exception["problemMessage"], e)
                        break
                    default:
                        saveProblemMessage(exception, exception["problemMessage"], stage, osName)
                }
                if (exception["enableGithubNotification"]) {
                    GithubNotificator.updateStatus(blockOptions["options"]["stage"], blockOptions["title"], exception["githubNotification"]["status"], 
                        blockOptions["env"], blockOptions["options"], exception["githubNotification"]["message"], blockOptions["buildUrl"])
                }
                return
            }
        }
        // exception wasn't found in configuration. Rethrow it
        throw e
    }
}


def saveProblemMessage(Map exception, String message, String stage = "", String osName = "") {
    def messageFunciton
    // find function for specified scope
    switch(exception["scope"]) {
        case ProblemMessageManager.GENERAL:
            messageFunciton = problemMessageManager.saveGeneralFailReason
            break
        case ProblemMessageManager.GLOBAL:
            messageFunciton = problemMessageManager.saveGlobalFailReason
            break
        default:
            // SPECIFIC scope is default scope
            messageFunciton = problemMessageManager.saveSpecificFailReason
    }
    if (osName) {
        messageFunciton(message, stage, osName) 
    } else if (stage) {
        messageFunciton(message, stage) 
    } else {
        messageFunciton(message) 
    }
}