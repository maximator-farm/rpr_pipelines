import groovy.json.JsonSlurper


def executeDeploy(Map testsBuildsIds, Map options) {
    //TODO add deploy logic
}


def call(String testsBranch = "master",
    String jsonTestsBuildsIds = "",
    String jsonOptions = "master") {

    try {
        // parse converted ids of tests builds and options
        Map testsBuildsIds = new HashMap<>(new JsonSlurper().parseText(jsonTestsBuildsIds))
        Map options = new HashMap<>(new JsonSlurper().parseText(jsonOptions))

        executeDeploy(testsBuildsIds, options)
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
