import java.text.SimpleDateFormat;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import hudson.plugins.git.GitException;
import java.nio.channels.ClosedChannelException;
import hudson.remoting.RequestAbortedException;
import java.lang.IllegalArgumentException;
import groovy.json.JsonBuilder
import jenkins.model.Jenkins


def executePlatform(String osName, String gpuNames, def executeBuild, Map options, Map testsBuildsIds) {
    def retNode = {
        try {
            node("${osName} && ${options.BUILDER_TAG}") {
                println("Started build at ${NODE_NAME}")
                stage("Build-${osName}") {
                    timeout(time: "${options.BUILD_TIMEOUT}", unit: 'MINUTES') {
                        ws("WS/${options.PRJ_NAME}_Build") {
                            executeBuild(osName, options)
                        }
                    }
                }
            }
            Map currentTestsBuildsIds = [:]
            String launchTestsStageName = "Launch-Tests-${osName}"
            def jobsCount = 0
            stage(launchTestsStageName) {
                def testTasks = [:]
                // get build number of current master job as id for tests jobs  
                options.globalId = env.BUILD_NUMBER
                // convert options Map to json to specify it as String parameter for called job
                def jsonOptions = new JsonBuilder(options).toPrettyString()
                gpuNames.split(',').each() {
                    String asicName = it
                    options.testsList = options.testsList ?: ['']

                    options.testsList.each() { testName ->
                        String currentJobName = "${asicName}-${osName}-${testName}"
                        def testsBuild = build(
                            job: options.testsJobName,
                            parameters: [
                                [$class: 'StringParameterValue', name: 'BuildName', value: "${currentJobName}-${options.globalId}"],
                                [$class: 'StringParameterValue', name: 'AsicName', value: asicName],
                                [$class: 'StringParameterValue', name: 'OsName', value: osName],
                                [$class: 'StringParameterValue', name: 'TestName', value: testName],
                                [$class: 'StringParameterValue', name: 'Options', value: jsonOptions]
                            ]
                        )
                        jobsCount++
                        currentTestsBuildsIds[currentJobName] = testsBuild.number
                    }
                }
                // write jobs which were run for this platform in common map
                testsBuildsIds << currentTestsBuildsIds

                println("[INFO] During ${launchTestsStageName} ${jobsCount} tests jobs were launched:")
                for (element in currentTestsBuildsIds) {
                    String formattedKey = sprintf("%-50s", "${element.key}")
                    println("[INFO] ${formattedKey}: run build with number #${element.value}")
                }

            }
        }
        catch (e) {
            println "[ERROR] executePlatform throw the exception"
            println "Exception: ${e.toString()}"
            println "Exception message: ${e.getMessage()}"
            println "Exception cause: ${e.getCause()}"
            println "Exception stack trace: ${e.getStackTrace()}"

            currentBuild.result = "FAILURE"
            options.FAILED_STAGES.add(e.toString())
            throw e
        }
    }
    return retNode
}

def call(def platforms, def executePreparation, def executePreBuild, def executeBuild, Map options) {

    try {
        stage("Preparation") {
            executePreparation(options)
        }

        //TODO call executePreBuild method and report testsList initialization from this pipeline
        options.testsList = options.tests

        def date = new Date()
        dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
        options.JOB_STARTED_TIME = dateFormatter.format(date)

        timestamps {
            String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
            String REF_PATH="${PRJ_PATH}/ReferenceImages"
            String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
            options['PRJ_PATH']="${PRJ_PATH}"
            options['REF_PATH']="${REF_PATH}"
            options['JOB_PATH']="${JOB_PATH}"

            // if timeout doesn't set - use default
            // value in minutes
            options['PREBUILD_TIMEOUT'] = options['PREBUILD_TIMEOUT'] ?: 60
            options['BUILD_TIMEOUT'] = options['BUILD_TIMEOUT'] ?: 60
            options['TEST_TIMEOUT'] = options['TEST_TIMEOUT'] ?: 60

            options['FAILED_STAGES'] = []

            List platformList = []
            List testResultList = []
            Map testsBuildsIds = [:]

            try {
                platforms.split(';').each() {
                    List tokens = it.tokenize(':')
                    String osName = tokens.get(0)
                    String gpuNames = ""
                    if (tokens.size() > 1) {
                        gpuNames = tokens.get(1)
                    }

                    platformList << osName
                    if(gpuNames) {
                        gpuNames.split(',').each() {
                            // if not split - testsList doesn't exists
                            options.testsList = options.testsList ?: ['']
                            options['testsList'].each() { testName ->
                                String asicName = it
                                String testResultItem = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                testResultList << testResultItem
                            }
                        }
                    }
                    tasks[osName]=executePlatform(osName, gpuNames, executeBuild, options, testsBuildsIds)
                }
                parallel tasks

                // get names of all incompleted builds
                List incompletedTestsBuilds = testsBuildsIds.keySet() as List
                while (!incompletedTestsBuilds.isEmpty()) {
                    // remove completed builds from incompletedTestsBuilds after iteration for avoid ConcurrentModificationException
                    List completedTestsBuilds = []
                    for (buildName in incompletedTestsBuilds) {
                        def build = Jenkins.instance.getItem(options.testsJobName).getBuildByNumber(testsBuildsIds[buildName])
                        // if build completed with any result, add it to completedTestsBuilds
                        if (build.getResult != 'BUILDING') {
                            completedTestsBuilds.add(buildName)
                        }
                    }
                    incompletedTestsBuilds.removeAll(completedTestsBuilds)

                    println("[INFO] Tests builds checked. Now running ${incompletedTestsBuilds.size} builds:")
                    for (element in currentTestsBuildsIds) {
                        String formattedKey = sprintf("%-50s", "${element.key}")
                        println("[INFO] ${formattedKey} running (build number #${element.value})")
                    }
                    //TODO add param for control output info (short or complete)
                    // wait next check
                    sleep(time: options.WAIT_TIEMOUT, unit: 'MINUTES')
                }

            }
            finally {
                //TODO run report job
            }
        }
    }
    catch (FlowInterruptedException e) {
        println(e.toString());
        println(e.getMessage());
        echo "Job was ABORTED by user. Job status: ${currentBuild.result}"
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILURE"
        throw e
    }
    finally {
        println "[INFO] BUILD RESULT: ${currentBuild.result}"
        println "[INFO] BUILD CURRENT RESULT: ${currentBuild.currentResult}"
    }
}
