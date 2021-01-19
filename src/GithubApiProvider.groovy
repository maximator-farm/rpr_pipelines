import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput


class GithubApiProvider {

    def context

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

    def createOrUpdateStatusCheck(Map params) {
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
        params["status"] = CheckStatusesMapping[params["status"]]

        def response = context.httpRequest(
            url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/check-runs",
            authentication: "jenkinsCredentials",
            httpMode: "POST",
            requestBody: JsonOutput.toJson(params),
            customHeaders: [
                [name: "Content-type", value: "application/json"]
            ]
        )

        return parseResponse(response)
    }

    def getStatusChecks(Map params) {
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
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

        def response = context.httpRequest(
            url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/${commitSHA}/check-runs?${queryParams}",
            authentication: "jenkinsCredentials",
            httpMode: "GET"
        )

        return parseResponse(response)
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