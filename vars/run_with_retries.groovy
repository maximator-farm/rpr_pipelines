def call(String labels, def stageTimeout, def retringFunction, Boolean reuseLastNode, def stageName, def options, List allowedExceptions = [], Integer maxNumberOfRetries = -1) {
    List nodesListAll = nodesByLabel label: labels, offline: true
    List nodesListOnline = nodesByLabel label: labels, offline: false
    println "[INFO] Found ${nodesListOnline.size()} suitable online nodes (total suitable nodes: ${nodesListAll.size()})"
    // if less than 2 suitable online nodes are found and some nodes are offline - sleep and retry search
    if (nodesListOnline.size() < 2 && nodesListAll.size() != nodesListOnline.size()) {
        println "[INFO] Too few nodes found. Search will be retried after pause"
        sleep(time: 10, unit: 'MINUTES')
        nodesListOnline = nodesByLabel label: labels, offline: false
    }
    println "Found the following PCs: ${nodesListOnline}"
    def nodesCount = nodesListOnline.size()
    def tries = nodesCount

    if (reuseLastNode) {
        tries++
    } else {
        // if there is only one suitable machine and reuseLastNode is false - forcibly add one more try
        if (tries == 1) {
            tries++
            reuseLastNode = true
        }
    }

    if (maxNumberOfRetries != -1 && tries > maxNumberOfRetries) {
        tries = maxNumberOfRetries
    }

    Boolean successCurrentNode = false
    options['nodeReallocateTries'] = tries

    for (int i = 0; i < tries; i++)
    {
        node(labels)
        {
            timeout(time: "${stageTimeout}", unit: 'MINUTES')
            {
                ws("WS/${options.PRJ_NAME}_${stageName}") {
                    successCurrentNode = false

                    try {
                        retringFunction(nodesListOnline)
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
                        } else if (exceptionClassName.contains("RemotingSystemException")) {
                            String nodeName = env.NODE_NAME
                            try {
                                // take master node for send exception in Slack channel
                                node ("master") {
                                    withCredentials([string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                                        utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "${nodeName}: RemotingSystemException appeared. Node is going to be marked as offline")
                                        utils.markNodeOffline(this, nodeName, "RemotingSystemException appeared. This node was marked as offline")
                                        utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "${nodeName}: Node was marked as offline")
                                    }
                                }
                            } catch (e2) {
                                node ("master") {
                                    withCredentials([string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                                        utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "Failed to mark node '${nodeName}' as offline")
                                    }
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
                    // exclude label of failed machine only if it isn't necessary to reuse last node and if it isn't last try
                    } else if (!(reuseLastNode && i == nodesCount - 1)) {
                        println "[EXCLUDE] ${env.NODE_NAME} from nodes pool (Labels: ${labels})"
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