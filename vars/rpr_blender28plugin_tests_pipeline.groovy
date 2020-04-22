import groovy.json.JsonSlurper


def executeTests(String osName, String asicName, Map options) {
    sleep(time: 10, unit: 'SECONDS')
}


def call(String testsBranch = "master",
    String asicName,
    String osName,
    String testName,
    String jsonOptions) {

    try {
    // parse converted options
    Map options = new HashMap<>(new JsonSlurper().parseText(jsonOptions))

        tests_launch_pipeline(this.&executeTests, asicName, osName, testName, options)
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}