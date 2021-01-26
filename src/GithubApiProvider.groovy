import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput


class GithubApiProvider {

    def context
    def installation_token

    GithubApiProvider(def context) {
        this.context = context
    }

    private def parseResponse(String response) {
        def jsonSlurper = new JsonSlurperClassic()

        return jsonSlurper.parseText(response)
    }

    private def buildQueryParams(Map params) {
        Map paramPairs = [:]
        for (param in params) {
            paramPairs << "${param}=${params[param]}"
        }
        return paramPairs.join("&")
    }

    private String receiveInstallationToken(String organization_name) {
        context.withCredentials([context.string(credentialsId: "githubNotificationAppKey", variable: "GITHUB_APP_KEY"),
            context.string(credentialsId: "githubNotificationAppId", variable: "GITHUB_APP_ID")]) {

            context.withEnv(["github_app_key=${GITHUB_APP_KEY}"]) {
                if (self.isUnix()) {
                    installation_token = python3("${context.CIS_TOOLS}/auth_github.py --github_app_id ${GITHUB_APP_ID} --organization_name ${organization_name}")
                } else {
                    installation_token = python3("${context.CIS_TOOLS}\\auth_github.py --github_app_id ${GITHUB_APP_ID} --organization_name ${organization_name}", false)
                }
            }

        }
    }

    private def withInstallationAuthorization(String organization_name, Closure function) {
        if (!installation_token) {
            receiveInstallationToken(organization_name)
        }
        def response = function()
        if (response.status == 401) {
            receiveInstallationToken(organization_name)
        }
        def response = function()
        if (response.status == 401) {
            throw new Exception("Could not authorize request")
        }
    }

    def createOrUpdateStatusCheck(Map params) {
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
        organization_name = repositoryUrl.split("/")[-2]
        params["status"] = CheckStatusesMapping[params["status"]]

        return withInstallationAuthorization(organization_name) {
            def response = context.httpRequest(
                url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/check-runs",
                httpMode: "POST",
                requestBody: JsonOutput.toJson(params),
                customHeaders: [
                    [name: "Content-type", value: "application/json"],
                    [name: "Authorization", value: "token ${installation_token}"]
                ],
                validResponseCodes: '0:401'
            )

            return parseResponse(response)
        }
    }

    def getStatusChecks(Map params) {
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
        organization_name = repositoryUrl.split("/")[-2]
        def commitSHA = params["commitSHA"]
        params.remove("commitSHA")

        String queryParams = ""
        if (params.containsKey("status")) {
            def status = CheckStatusesMapping[params["status"]]
            params.remove("status")
            queryParams = "status=${status}"
            if (params) {
                queryParams = "${queryParams}&${buildQueryParams[params]}"
            }
        }

        return withInstallationAuthorization(organization_name) {
            def response = context.httpRequest(
                url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/${commitSHA}/check-runs?${queryParams}",
                httpMode: "GET",
                customHeaders: [
                    [name: "Authorization", value: "token ${installation_token}"]
                ]
                validResponseCodes: '0:401'
            )

            return parseResponse(response)
        }
    }

    public static Map CheckStatusesMapping = [
        "success": "success",
        "pending": "queued",
        "failure": "failed",
        "error": "action_required",

        "in_progress": "in_progress",
        "neutral": "neutral",
        "timed_out": "timed_out",
        "canceled": "canceled",
    ]

}