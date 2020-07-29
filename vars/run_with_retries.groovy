def call(String labels, def stageTimeout, def retringFunction, Boolean reuseLastNode, def stageName, def options, List allowedExceptions = []) {
    def nodesList = nodesByLabel label: labels, offline: false
    println "Found the following PCs: ${nodesList}"
    def nodesCount = nodesList.size()
    def tries = nodesCount

    if (reuseLastNode) {
        tries++
    } else {
        if (tries == 1) {
            tries++
            reuseLastNode = true
        }
    }

    Boolean successCurrentNode = false

    for (int i = 0; i < tries; i++)
    {
        node(labels)
        {
            timeout(time: "${stageTimeout}", unit: 'MINUTES')
            {
                ws("WS/${options.PRJ_NAME}_${stageName}") {
                    successCurrentNode = false

                    try {
                        retringFunction(nodesList)
                        successCurrentNode = true
                    } catch(Exception e) {
                        String exceptionClassName = e.getClass().toString()

                        if (exceptionClassName.contains("FlowInterruptedException")) {
                            e.getCauses().each(){
                                // UserInterruption aborting by user
                                // ExceededTimeout aborting by timeout
                                // CancelledCause for aborting by new commit
                                String causeClassName = it.getClass().toString()
                                println "Interruption cause: ${causeClassName}"
                                if (causeClassName.contains("CancelledCause")) {
                                    println "GOT NEW COMMIT"
                                    throw e
                                } else if (causeClassName.contains("UserInterruption")) {
                                    println "[INFO] Build was aborted by user"
                                    throw e
                                }
                            }
                        }

                        println "[ERROR] Failed during tests on ${env.NODE_NAME} node"
                        println "Exception: ${e.toString()}"
                        println "Exception message: ${e.getMessage()}"
                        println "Exception cause: ${e.getCause()}"
                        println "Exception stack trace: ${e.getStackTrace()}"

                        if (allowedExceptions.size() != 0) {
                            Boolean isExceptionAllowed = false

                            for (allowedException in allowedExceptions) {
                                if (exceptionClassName.contains(allowedException)) {
                                    isExceptionAllowed = true
                                    break
                                }
                            }

                            if (!isExceptionAllowed) {
                                println("[INFO] Exception isn't allowed")
                                throw e
                            } else {
                                println("[INFO] Exception found in allowed exceptions")
                            }
                        }
                    }

                    if (successCurrentNode) {
                        i = tries + 1
                    } else if ((!reuseLastNode && i < nodesCount) || (reuseLastNode && i < nodesCount - 1)) {
                        println "[INFO] Updating label after failure. Adding !${env.NODE_NAME} to labels list for ${labels}."
                        labels += " && !${env.NODE_NAME}"
                    }
                }
            }
        }
    }
    if (!successCurrentNode) {
        currentBuild.result = "FAILURE"
        println "[ERROR] All nodes on ${stageName} stage with labels ${labels} failed."
    }
}