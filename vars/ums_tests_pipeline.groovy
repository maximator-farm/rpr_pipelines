import groovy.transform.Field
import universe.*


@Field final String PRODUCT_NAME = "UMS+Tests"


/**
* Tests execution methods
*
* @param testsBranch jobs_launcher branch
* @param envs List of envs labels
* @param test_groups List of test groups names
*/
def execute(
    String testsBranch,
    List envs,
    List test_groups
) {
    def client = null

    withCredentials([string(credentialsId: "devTestUmsURL", variable: "TEST_UMS_URL"),
            string(credentialsId: "imageServiceURL", variable: "IS_URL")]) {

        umsURL = TEST_UMS_URL
        imageServiceURL = IS_URL
        println("[INFO] START Create UMS client")
        client = new UniverseClient(this, umsURL, env, imageServiceURL, PRODUCT_NAME)
        println("[INFO] END Create UMS client")
    }

    println("[INFO] START Setup token")
    client.tokenSetup()
    println("[INFO] END Setup Token")

    println("[INFO] START Create build")
    withCredentials([string(credentialsId: "devTestFrontUmsURL", variable: "TEST_UMS_FRONT_URL")]) {
        client.createBuild(envs, test_groups, false, null, TEST_UMS_FRONT_URL, "test")
    }
    println("[INFO] END Create build")

    BUILD_ID = client.build["id"]
    println("[INFO] BUILD_ID = ${BUILD_ID}")
    JOB_ID = client.build["job_id"]
    println("[INFO] JOB_ID = ${JOB_ID}")

    cleanWS("Ubuntu")
    checkoutScm(branchName: testsBranch, repositoryUrl: "https://github.com/luxteam/jobs_launcher.git")

    client.stage("Tests", "begin");

    println("[INFO] START Tests execution")
    envs.each { env_label ->
        println("[INFO] START Environment tests execution")
        withCredentials([usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD')]) {
            withEnv(["UMS_URL=${umsURL}",
                "UMS_USER=${UMS_USER}", "UMS_PASSWORD=${UMS_PASSWORD}",
                "UMS_BUILD_ID=${BUILD_ID}", "UMS_JOB_ID=${JOB_ID}",
                "UMS_ENV_LABEL=${env_label}", "UMS_TEST_GROUPS=${test_groups.join(',')}"
            ]) {
                sh """
                    python3 -m pip install pytest
                    sh ./run_ums_tests.sh
                """
            }
        }
    }
    println("[INFO] END Tests execution")

    client.stage("Tests", "end");
}

/**
* Pipeline for run UMS tests
*
* @param testsBranch jobs_launcher branch
*/
def call(
    String testsBranch
) {

    node("!UMS") {
        stage("Execute Tests") {
            execute(
                testsBranch,
                ["Windows", "Ubuntu"],
                ["Smoke", "Example"]
            )
        }
    }
}