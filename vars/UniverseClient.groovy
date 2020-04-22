 
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic;
// imports for work with JSONs

class UniverseClient {
    def context;
    def url;
    def token;
    def build;
    def env

    UniverseClient(context, url, env) {
        this.url = url;
        this.context = context;
        this.env = env;
    }

    def retryWrapper(func) {
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
                func.call()
                return true
            } catch(error) {
                this.context.println(error)
                this.context.sleep(timeout)
                // timeout = timeout + 30
                return false
            }
        }
    }

    def tokenSetup(cred) {
        def response = this.context.httpRequest consoleLogResponseBody: true, authentication: "bed7fc08-c86e-4299-baa8-d5ca19517bd2", httpMode: 'POST',  url: "${this.url}/user/login", validResponseCodes: '200'
        def token = this.context.readJSON text: "${response.content}"
        this.token = "${token['token']}"
        this.context.echo this.token
    }

    def createBuild(envs, suites) {
        def request = {
            def buildBody = [
                'name': env.BUILD_NUMBER,
                'envs': envs,
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
                url: "${this.url}/api/build?jobName=${env.JOB_NAME}",
                validResponseCodes: '200'
            )
            
            def jsonSlurper = new JsonSlurperClassic()
            def content = jsonSlurper.parseText(res.content);
            this.build = content["build"];
            this.context.echo content["msg"];
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
                validResponseCodes: '200'
            )
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
                validResponseCodes: '200'
            )
        }
        retryWrapper(request)
    }
}