 
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic;
// imports for work with JSONs

class UniverseClient {
    def context;
    def url;
    def product;
    def token;
    def build;
    def env;
    def is_url;

    UniverseClient(context, url, env, is_url, product) {
        this.url = url;
        this.context = context;
        this.env = env;
        this.is_url = is_url;
        this.product = product;
    }

    def retryWrapper(func, validResponseCodes = [200]) {
        def attempt = 0
        def attempts = 5
        def timeout = 30 // seconds

        this.context.waitUntil {
            if (attempt == attempts) {
                println("Attempts: 0. Exit.")
                return true
            }

            attempt++
            this.context.println("Attempt: ${attempt}")

            try {
                def response = func.call()
                if( validResponseCodes.contains(response.status)){
                    return true
                }
                switch(response.status){
                    case 401:
                        this.tokenSetup()
                        break;
                }
                return false

            } catch(error) {
                this.context.println(error)
                this.context.sleep(timeout)
                // timeout = timeout + 30
                return false
            }
        }
    }

    def tokenSetup(cred) {
        def response = this.context.httpRequest consoleLogResponseBody: true, authentication: 'universeMonitoringSystem', httpMode: 'POST',  url: "${this.url}/user/login", validResponseCodes: '200'
        def token = this.context.readJSON text: "${response.content}"
        this.token = "${token['token']}"
        this.context.echo this.token
    }

    def createBuild(envs, suites) {
        println "BUILD_TAG = ${env.BUILD_TAG}"
        println "BUILD_URL = ${env.BUILD_URL}"
        println "JOB_NAME = ${env.JOB_NAME}"
        String tag = "Other"
        String job_name = env.JOB_NAME.toLowerCase()
        if (job_name.contains("weekly")) {
            tag = "Weekly"
        } else if (job_name.contains("manual")){
            tag = "Manual"
        } else if (job_name.contains("auto")) {
            tag = "Auto"
        }
        def request = {
            def buildBody = [
                'name': env.BUILD_NUMBER,
                'envs': envs,
                'tag': tag,
                'suites': suites
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'POST',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?jobName=${this.product}",
                validResponseCodes: '0:599'
            )
            
            def jsonSlurper = new JsonSlurperClassic()
            def content = jsonSlurper.parseText(res.content);
            this.build = content["build"];
            this.context.echo content["msg"];
            return res;
        }
        retryWrapper(request)
    }


    def stage(name, action) {
        // String name - stage name, String action - enum ["begin", "end"]
        def request = {
            def buildBody = [
                'stage': [
                    'name': name,
                    'action': action
                ]
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )
            return res;
        }
        retryWrapper(request)
    }

    def sendInfo(info) {
        // String name - stage name, String action - enum ["begin", "end"]
        def request = {
            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(info),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )

            return res;
        }
        retryWrapper(request)
    }

    def changeStatus(status) {
        def request = {
            def mapStatuses = [
                "FAILURE": "error",
                "FAILED": "error",
                "UNSTABLE": "failed",
                "ABORTED": "aborted",
                "SUCCESS": "passed",
                "SUCCESSFUL": "passed"
            ]

            def buildBody = [
                'status': mapStatuses[status]
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )

            return res;
        }
        retryWrapper(request)
    }
}