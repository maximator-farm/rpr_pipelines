import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;


def call(def executeTests , String asicName, String osName, String testName, Map options) {
    try {
        timestamps {
            println("Scheduling ${osName}:${asicName} ${testName}")

            def testerTag = options.TESTER_TAG ? "${options.TESTER_TAG} && Tester" : "Tester"
            // reallocate node for each test
            def nodeLabels = "${osName} && ${testerTag} && OpenCL && gpu${asicName}"
            def nodesList = nodesByLabel label: nodeLabels, offline: false
            println "Found the following PCs for the task: ${nodesList}"
            def nodesCount = nodesList.size()
            options.nodeReallocateTries = nodesCount + 1
            boolean successCurrentNode = false
            
            for (int i = 0; i < options.nodeReallocateTries; i++) {
                node(nodeLabels) {
                    println("[INFO] Launched ${testName} task at: ${env.NODE_NAME}")
                    options.currentTry = i
                    timeout(time: "${options.TEST_TIMEOUT}", unit: 'MINUTES') {
                        ws("WS/${options.PRJ_NAME}_Test") {
                            Map newOptions = options.clone()
                            newOptions['testResultsName'] = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                            newOptions['stageName'] = testName ? "${asicName}-${osName}-${testName}" : "${asicName}-${osName}"
                            newOptions['tests'] = testName ? testName : options.tests
                            try {
                                executeTests(osName, asicName, newOptions)
                                i = options.nodeReallocateTries + 1
                                successCurrentNode = true
                            } catch(Exception e) {
                                println "[ERROR] Failed during tests on ${env.NODE_NAME} node"
                                println "Exception: ${e.toString()}"
                                println "Exception message: ${e.getMessage()}"
                                println "Exception cause: ${e.getCause()}"
                                println "Exception stack trace: ${e.getStackTrace()}"

                                // change PC after first failed tries and don't change in the last try
                                if (i < nodesCount - 1 && nodesCount != 1) {
                                    println "[INFO] Updating label after failure task. Adding !${env.NODE_NAME} to labels list."
                                    nodeLabels += " && !${env.NODE_NAME}"
                                }
                            }
                        }
                    }
                }
            }
            if (!successCurrentNode) {
                currentBuild.result = "FAILED"
                println "[ERROR] All allocated nodes failed."
            }
        }
    }
    catch (FlowInterruptedException e) {
        println(e.toString());
        println(e.getMessage());
        echo "Job was ABORTED by user. Job status: ${currentBuild.result}"
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILURE"
        throw e
    }
    finally {
        println "[INFO] BUILD RESULT: ${currentBuild.result}"
        println "[INFO] BUILD CURRENT RESULT: ${currentBuild.currentResult}"
    }
}
