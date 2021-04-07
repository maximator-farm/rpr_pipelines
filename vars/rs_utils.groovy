import utils

class rs_utils {

    static def handleException(Object self, Exception e, String nodeName) {
        def env = self.env
        String exceptionClassName = e.getClass().toString()

        if (exceptionClassName.contains("RemotingSystemException")) {
            try {
                // take master node for send exception in Slack channel
                self.node("master") {
                    self.withCredentials([self.string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                        utils.sendExceptionToSlack(self, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, self.WEBHOOK_URL, "zabbix_critical", "${nodeName}: RemotingSystemException appeared. Node is going to be marked as offline")
                        utils.markNodeOffline(self, nodeName, "RemotingSystemException appeared. This node was marked as offline")
                        utils.sendExceptionToSlack(self, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, self.WEBHOOK_URL, "zabbix_critical", "${nodeName}: Node was marked as offline")
                    }
                }
            } catch (e2) {
                self.node("master") {
                    self.withCredentials([self.string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                        utils.sendExceptionToSlack(self, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, self.WEBHOOK_URL, "zabbix_critical", "Failed to mark node '${nodeName}' as offline")
                    }
                }
            }
        }
    }
}