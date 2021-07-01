def call(Map options) {

    if (currentBuild.result == "FAILURE") {

        String text 
        if (options["problemMessageManager"]) {
            text = options["problemMessageManager"].getMessages()
        } else {
            text = "Failed in:  ${options.FAILED_STAGES.join('\n')}"
        } 

        def debugSlackMessage = [[
            "title": "${env.JOB_NAME} [${env.BUILD_NUMBER}]",
            "title_link": "${env.BUILD_URL}",
            "color": "#fc0356",
            "pretext": "${currentBuild.result}",
            "text": text
        ]]

        try {
            if ((env.BRANCH_NAME && env.BRANCH_NAME == "master") || env.CHANGE_BRANCH || env.JOB_NAME.contains("Weekly")) {
                // FIXME: channel = "cis_failed_master"
                utils.sendMessageToSlack(this, "test_jenkins_messages", debugSlackMessage)
            } else {
                // FIXME: channel = env.debagChannel
                utils.sendMessageToSlack(this, "test_jenkins_messages", debugSlackMessage)
            }
        } catch (e) {
            println("Error during slack notification to debug channel")
            println(e.toString())
        }
    }

}
