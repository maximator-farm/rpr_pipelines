import groovy.transform.Field
import groovy.json.JsonOutput
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import static groovy.io.FileType.FILES
import TestsExecutionType
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException


def executeTestCommand(String osName, String asicName, Map options)
{
    switch (osName) {
        case 'Windows':

            if (options.recreateCondaEnv) {
                    bat """
                        call C:\\Users\\${env.USERNAME}\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env remove --name upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env create --force --quiet --name upscalers_pytorch -f upscalers_pytorch.yml -v >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda activate upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        call ipython kernel install --user --name upscalers_pytorch
                        pip install C:\\AMD\\OpenEXR-1.3.2-cp38-cp38-win_amd64.whl >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install -e . >> ${STAGE_NAME}_init_env.log 2>&1
                        mkdir nbs\\tested
                        call delete_auto_generated_files_upscaler
                    """
                } else {
                    bat """
                        call C:\\Users\\${env.USERNAME}\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda env update --prune --quiet --name upscalers_pytorch -f upscalers_pytorch.yml -v >> ${STAGE_NAME}_init_env.log 2>&1
                        call conda activate upscalers_pytorch >> ${STAGE_NAME}_init_env.log 2>&1
                        call ipython kernel install --user --name upscalers_pytorch
                        call jupyter kernelspec list >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install C:\\AMD\\OpenEXR-1.3.2-cp38-cp38-win_amd64.whl >> ${STAGE_NAME}_init_env.log 2>&1
                        pip install -e . >> ${STAGE_NAME}_init_env.log 2>&1
                        mkdir nbs\\tested
                        call delete_auto_generated_files_upscaler
                    """
                }
            }

            for (test in options.tests) {
                dir ("nbs") {
                    try {
                        if (fileExists("${test}.ipynb")) {
                            GithubNotificator.updateStatus("Test", "${asicName}-${osName}-${test}", "in_progress", options, NotificationConfiguration.EXECUTE_TEST, BUILD_URL)
                            println "[INFO] Current notebook: ${test}.ipynb"
                            bat """
                                call C:\\Users\\${env.USERNAME}\\anaconda3\\Scripts\\activate.bat >> ${STAGE_NAME}_init_env.log 2>&1
                                call conda activate upscalers_pytorch >> ${STAGE_NAME}_${test}.log 2>&1
                                jupyter nbconvert --to html --execute --ExecutePreprocessor.timeout=${options.notebooksTimeout} --ExecutePreprocessor.kernel_name=upscalers_pytorch --output tested/tested_${test} ${test}.ipynb >> ..\\${STAGE_NAME}_${test}.log 2>&1

                            """
                            utils.publishReport(this, BUILD_URL, "tested", "tested_${test}.html", "${test} report", "Test Report")
                            GithubNotificator.updateStatus("Test", "${asicName}-${osName}-${test}", "success", options, NotificationConfiguration.TEST_PASSED, "${BUILD_URL}/${test.replace("_", "_5f")}_20report")
                        } else {
                            currentBuild.result = "UNSTABLE"
                            GithubNotificator.updateStatus("Test", "${asicName}-${osName}-${test}", "failure", options, NotificationConfiguration.TEST_NOT_FOUND, BUILD_URL)
                            options.problemMessageManager.saveUnstableReason("tested_${test}.html wasn't found\n")
                            println "[WARNING] ${test}.ipynb wasn't found"
                        }
                    } catch (FlowInterruptedException error) {
                        println("[INFO] Job was aborted during executing tests.")
                        throw error
                    } catch (e) {
                        currentBuild.result = "UNSTABLE"
                        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
                        GithubNotificator.updateStatus("Test", "${asicName}-${osName}-${test}", "failure", options, NotificationConfiguration.TEST_FAILED, "${BUILD_URL}/artifact/${STAGE_NAME}_${test}.log")
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
        
        timeout(time: "10", unit: "MINUTES") {
            cleanWS(osName)
            checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo)
        }

        // Manual download only (~160GB)
        //withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
        //    String assets_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/upscalers_pytorch_assets" : "/mnt/c/TestResources/upscalers_pytorch_assets"
        //    downloadFiles("/volume1/ml-data/upscalers_pytorch_assets/", assets_dir)
        //}

        outputEnvironmentInfo(osName, "${STAGE_NAME}_init_env")
        executeTestCommand(osName, asicName, options)

    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        
        if (e instanceof ExpectedExceptionWrapper) {
            throw new ExpectedExceptionWrapper(e.getMessage(), e.getCause())
        } else {
            throw new ExpectedExceptionWrapper(NotificationConfiguration.REASON_IS_NOT_IDENTIFIED, e)
        }
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executeBuild(String osName, Map options) {}


def executePreBuild(Map options)
{
    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
        checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, disableSubmodules: true)
    }

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

    if (env.BRANCH_NAME) {
        withNotifications(title: "Jenkins build configuration", printMessage: true, options: options, configuration: NotificationConfiguration.CREATE_GITHUB_NOTIFICATOR) {
            GithubNotificator githubNotificator = new GithubNotificator(this, options)
            githubNotificator.init(options)
            options.githubNotificator = githubNotificator
            githubNotificator.initPreBuild(BUILD_URL)
        }
    }

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        if (options.executeAllTests) {
            dir ("nbs") {
                options.tests = []
                def ipynb_files = findFiles(glob: "*.ipynb")
                for (file in ipynb_files) {
                    options.tests << file.name.replaceFirst(~/\.[^\.]+$/, '')
                }
            }
        } else {
            options.tests = options.tests.split(" ")
        }
        println "[INFO] Tests to be executed: ${options.tests}"
    }

    if (env.BRANCH_NAME && options.githubNotificator) {
        options.githubNotificator.initChecks(options, BUILD_URL, true, false, false)
    }
    
}


def executeDeploy(Map options, List platformList, List testResultList) {}


def call(String projectBranch = "",
         String platforms = 'Windows:NVIDIA_RTX5000',
         Boolean executeAllTests = true,
         String tests = "0000_index,0010_config,0015_utils,0018_pytorch_utils,0020_plot,0030_image,0050_colormaps,0070_SSIM",
         String customTests = "",
         String notebooksTimeout = 300,
         Boolean recreateCondaEnv = false) {

    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [stage: "Init", problemMessageManager: problemMessageManager]

    println "Selected tests: ${tests}"
    println "Additional tests: ${customTests}"
    tests = tests.replace(",", " ") + " " + customTests.replace(", ", " ")
    println "All tests to be run: ${tests}"
    println "Notebooks timeout: ${notebooksTimeout}"

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
                        notebooksTimeout:notebooksTimeout,
                        recreateCondaEnv:recreateCondaEnv,
                        executeBuild:false,
                        executeTests:true,
                        TEST_TIMEOUT:60,
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
