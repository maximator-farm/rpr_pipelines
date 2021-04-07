import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import hudson.model.Result
import groovy.json.JsonOutput

/**
 * self in methods params is a context of executable pipeline. Without it you can't call Jenkins methods.
 */
class utils {

    static def markNodeOffline(Object self, String nodeName, String offlineMessage) {
        try {
            def nodes = jenkins.model.Jenkins.instance.getLabel(nodeName).getNodes()
            nodes[0].getComputer().doToggleOffline(offlineMessage)
            self.println("[INFO] Node '${nodeName}' was marked as failed")
        } catch (e) {
            self.println("[ERROR] Failed to mark node '${nodeName}' as offline")
            self.println(e)
            throw e
        }
    }

    static def sendExceptionToSlack(Object self, String jobName, String buildNumber, String buildUrl, String webhook, String channel, String message) {
        try {
            def slackMessage = [
                attachments: [[
                    "title": "${jobName} [${buildNumber}]",
                    "title_link": "${buildUrl}",
                    "color": "#720000",
                    "text": message
                ]],
                channel: channel
            ]
            self.httpRequest(
                url: webhook,
                httpMode: 'POST',
                requestBody: JsonOutput.toJson(slackMessage)
            )
            self.println("[INFO] Exception was sent to Slack")
        } catch (e) {
            self.println("[ERROR] Failed to send exception to Slack")
            self.println(e)
        }
    }
    
    @NonCPS
    static Boolean isNodeIdle(String nodeName) {
        return jenkins.model.Jenkins.instance.getNode(nodeName).getComputer().countIdle() > 0
    }
}