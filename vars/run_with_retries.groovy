def call(String labels, def stageTimeout, def retringFunction, Boolean reuseLastNode, def stageName, def options) {
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
                    Map functionOptions = ['successCurrentNode': successCurrentNode]
                    retringFunction(functionOptions, nodesList)
                    successCurrentNode = functionOptions['successCurrentNode']

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