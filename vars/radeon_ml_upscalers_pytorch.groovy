import groovy.transform.Field
import groovy.json.JsonOutput
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import static groovy.io.FileType.FILES
import TestsExecutionType


def executeTestCommand(String osName, String asicName, Map options)
{
    switch (osName) {
        case 'Windows':

            if (options.recreateCondaEnv) {
                    bat """
                        call C:\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env remove --name upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env create --force --quiet --name upscalers_pytorch -f upscalers_pytorch.yml -v >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda activate upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install C:\\TestResources\\OpenEXR-1.3.2-cp38-cp38-win_amd64.whl >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install -e . >> ${STAGE_NAME}_init_env.log 2>&1
                        mkdir nbs\\tested
                    """
                } else {
                    bat """
                        call C:\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env update --prune --quiet --name upscalers_pytorch -f upscalers_pytorch.yml -v >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda activate upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install C:\\TestResources\\OpenEXR-1.3.2-cp38-cp38-win_amd64.whl >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install -e . >> ${STAGE_NAME}_init_env.log 2>&1
                        mkdir nbs\\tested
                    """
                }
            }

            println "Tests: ${options.parsedTests}"
            for (test in options.parsedTests) {
                dir ("nbs") {
                    try {
                        if (fileExists("${test}.ipynb")) {
                            println "[INFO] Current notebook: ${test}.ipynb"
                            bat """
                                set TAAU_DATA=C:\\TestResources\\upscalers_pytorch_assets\\data_small
                                call C:\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_${test}.log 2>&1
                                call conda activate upscalers_pytorch >> ${STAGE_NAME}_${test}.log 2>&1
                                jupyter nbconvert --to html --execute --ExecutePreprocessor.timeout=300 --output tested/tested_${test} ${test}.ipynb >> ..\\${STAGE_NAME}_${test}.log 2>&1
                            """
                        } else {
                            currentBuild.result = "UNSTABLE"
                            options.problemMessageManager.saveUnstableReason("tested_${test}.html wasn't found\n")
                            println "[WARNING] tested_${test}.html wasn't found"
                        }
                    } catch (e) {
                        currentBuild.result = "UNSTABLE"
                        options.problemMessageManager.saveUnstableReason("Failed to execute ${test}\n")
                        println "[ERROR] Failed to execute ${test}"
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
            }
}


def executeTests(String osName, String asicName, Map options)
{
    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "10", unit: "MINUTES") {
                cleanWS(osName)
                checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo)
            }
        }

        // Manual download only (~160GB)
        //withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
        //    String assets_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/upscalers_pytorch_assets" : "/mnt/c/TestResources/upscalers_pytorch_assets"
        //    downloadFiles("/volume1/ml-data/upscalers_pytorch_assets/", assets_dir)
        //}

        outputEnvironmentInfo(osName, "${STAGE_NAME}_init_env")

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
            executeTestCommand(osName, asicName, options)
        }

        options.executeTestsFinished = true

    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${e.getMessage()}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(e.getMessage(), e.getCause())
        } else {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED} ${additionalDescription}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(NotificationConfiguration.REASON_IS_NOT_IDENTIFIED, e)
        }
    } finally {
        try {
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
            dir("nbs") {
                for (test in options.parsedTests) {
                    try {
                        // Save test data for access it manually anyway
                        if (fileExists("tested/tested_${test}.html")) {
                            utils.publishReport(this, "${BUILD_URL}", "tested", "tested_${test}.html", "${test} report", "Test Report")
                        } else {
                            println "[WARNING] tested_${test}.html wasn't found"
                        }
                    } catch(e) {
                        println("[WARNING] Failed to publish ${test} report.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
            }
            
        } catch (e) {
            // throw exception in finally block only if test stage was finished
            if (options.executeTestsFinished) {
                if (e instanceof ExpectedExceptionWrapper) {
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, e.getMessage(), "${BUILD_URL}")
                    throw e
                } else {
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.FAILED_TO_SAVE_RESULTS, "${BUILD_URL}")
                    throw new ExpectedExceptionWrapper(NotificationConfiguration.FAILED_TO_SAVE_RESULTS, e)
                }
            }
        }
    }
}

def executeBuild(String osName, Map options) {}


def executePreBuild(Map options)
{
    checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, disableSubmodules: true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    currentBuild.description = "<b>GitHub repo:</b> ${options.projectRepo}<br/>"

    if (options.projectBranch){
        currentBuild.description += "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description += "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (options.executeAllTests) {
        dir ("nbs") {
            options.parsedTests = []
            def ipynb_files = findFiles(glob: "*.ipynb")
            for (file in ipynb_files) {
                options.parsedTests << file.name.replaceFirst(~/\.[^\.]+$/, '')
            }
        }
    } else {
        options.parsedTests = options.tests.split(" ")
    }
    println "Parsed tests: ${options.parsedTests}"
    
}


def executeDeploy(Map options, List platformList, List testResultList)
{
}



def call(String projectBranch = "",
         String platforms = 'Windows:NVIDIA_RTX2080',
         Boolean executeAllTests = false,
         String tests = "0000_index,0010_config,0015_utils,0018_pytorch_utils,0020_plot,0030_image,0050_colormaps,0070_SSIM",
         String customTests = "",
         Boolean recreateCondaEnv = false) {

    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [stage: "Init", problemMessageManager: problemMessageManager]

    println "Selected tests: ${tests}"
    println "Additional tests: ${customTests}"
    tests += " " + customTests

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            println "Platforms: ${platforms}"

            options << [projectRepo:"git@github.com:Radeon-Pro/upscalers_pytorch.git",
                        projectBranch:projectBranch,
                        PRJ_NAME:"upscalers_pytorch",
                        PRJ_ROOT:"rpr-ml",
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        executeAllTests:executeAllTests,
                        tests:tests,
                        recreateCondaEnv:recreateCondaEnv,
                        executeBuild:false,
                        executeTests:true,
                        retriesForTestStage:1]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {
        problemMessageManager.publishMessages()
    }

}
