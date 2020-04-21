import groovy.json.JsonSlurper


def executeDeploy(Map jsonTestsBuildsIds, Map jsonOptions) {
    //TODO add deploy logic
}


def call(String testsBranch = "master",
    String jsonTestsBuildsIds = "",
    String jsonOptions = "master") {

    try {
        // parse converted ids of tests builds and options
        Map testsBuildsIds = new JsonSlurper().parseText(jsonTestsBuildsIds)
        Map options = new JsonSlurper().parseText(jsonOptions)

        executeDeploy(jsonTestsBuildsIds, jsonOptions)
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
