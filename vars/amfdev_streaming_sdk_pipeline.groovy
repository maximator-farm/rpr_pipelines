import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import static autojobconfig.getConfig as getConfig


@Field final String PROJECT_REPO = "git@github.amd.com:AMD-Radeon-Driver/drivers.git"
@Field final String TESTS_REPO = "git@github.com:maximator-farm/jobs_test_streaming_sdk.git"
@Field final def SPARSE_CHECKOUT_PATH = ['make', 'drivers/amf', 'drivers/dal', 'drivers/make']
@Field final String BASE_PROJECT_DIR = "drivers\\amf\\stable"
@Field final String AMF_SOLUTION = "AMF_All"
@Field final String AMF_SOLUTION_DIR = "drivers\\amf\\stable\\build\\solution"
@Field final String AMF_BOOTSTRAP_REPO = "C:\\AMFSDK"
@Field final String AMF_THIRDPARTY = "drivers\\amf\\Thirdparty"
@Field final String BINARY_PACKAGER_SCRIPT = "drivers\\amf\\stable\\build\\package\\packageStreaming_SDK_Binaries.bat"
@Field final String BUILD_PACKAGE_PATH = "drivers\\amf\\stable\\build\\package"
//'games' : 'LoL,HeavenDX11,ApexLegends,ValleyDX11'
@Field final def LUXSDK_AUTOJOB_CONFIG = [
      'projectBranch' :             'origin/amd/stg/amf',
      'testsBranch' :               'origin/master',
      'platforms' :                 'Windows:Navi23',
      'clientTag' :                 'LuxSDK_Client',
      'winBuildConfiguration' :     'release',
      'winVisualStudioVersion' :    '2019',
      'winTestingBuildName' :       'release_vs2019',
      'androidBuildConfiguration' : 'release,debug',
      'games' :                     'HeavenDX9,HeavenDX11,LoL',
      'testsPackage' :              'General.json',
      'tests' :                     'General',
      'testerTag' :                 'StreamingSDK',
      'testCaseRetries' :           2,
      'clientCollectTraces' :       false,
      'serverCollectTraces' :       false,
      'storeOnNAS' :                false
]


String getClientLabels(Map options) {
    return "${options.osName} && ${options.TESTER_TAG} && ${options.CLIENT_TAG}"
}


Boolean isIdleClient(Map options) {
    def suitableNodes = nodesByLabel label: getClientLabels(options), offline: false

    for (node in suitableNodes) {
        if (utils.isNodeIdle(node)) {
            return true
        }
    }

    return false
}


def prepareTool(String osName, Map options) {
    switch(osName) {
        case "Windows":
            makeUnstash(name: "ToolWindows", unzip: false, storeOnNAS: options.storeOnNAS)
            unzip(zipFile: "${options.winTestingBuildName}.zip")
            break
        case "OSX":
            println("Unsupported OS")
            break
        default:
            println("Unsupported OS")
    }
}


def getServerIpAddress(String osName, Map options) {
    switch(osName) {
        case "Windows":
            return bat(script: "echo %IP_ADDRESS%",returnStdout: true).split('\r\n')[2].trim()
        case "OSX":
            println("Unsupported OS")
            break
        default:
            println("Unsupported OS")
    }
}


def getGPUName() {
    try {
        String renderDevice

        dir("jobs_launcher") {
            dir("core") {
                renderDevice = python3("-c \"from system_info import get_gpu; print(get_gpu())\"").split('\r\n')[2].trim()
            }
        }

        return renderDevice
    } catch (e) {
        println("[ERROR] Failed to get GPU name")
        throw e
    }
}


def getOSName() {
    try {
        String machineInfoRaw
        def machineInfoJson

        dir("jobs_launcher") {
            dir("core") {
                machineInfoRaw = python3("-c \"from system_info import get_machine_info; print(get_machine_info())\"").split('\r\n')[2].trim()
            }
        }

        machineInfoJson = utils.parseJson(this, machineInfoRaw.replaceAll("\'", "\""))
        return machineInfoJson["os"]
    } catch (e) {
        println("[ERROR] Failed to get OS name")
        throw e
    }
}


def getCommunicationPort(String osName, Map options) {
    switch(osName) {
        case "Windows":
            return bat(script: "echo %COMMUNICATION_PORT%",returnStdout: true).split('\r\n')[2].trim()
        case "OSX":
            println("Unsupported OS")
            break
        default:
            println("Unsupported OS")
    }
}


def getClientScreenWidth(String osName, Map options) {
    try {
        switch(osName) {
            case "Windows":
                return powershell(script: "wmic path Win32_VideoController get CurrentHorizontalResolution", returnStdout: true).split()[-1].trim()
            case "OSX":
                println("Unsupported OS")
                break
            default:
                println("Unsupported OS")
        }
    } catch (e) {
        println("[ERROR] Failed to get client screen width")
        println(e)

        return 1920
    }
}


def getClientScreenHeight(String osName, Map options) {
    try {
        switch(osName) {
            case "Windows":
                return powershell(script: "wmic path Win32_VideoController get CurrentVerticalResolution", returnStdout: true).split()[-1].trim()
            case "OSX":
                println("Unsupported OS")
                break
            default:
                println("Unsupported OS")
        }
    } catch (e) {
        println("[ERROR] Failed to get client screen height")
        println(e)

        return 1080
    }
}


def closeGames(String osName, Map options, String gameName) {
    try {
        switch(osName) {
            case "Windows":
                if (gameName == "All") {
                    bat """
                        taskkill /f /im \"borderlands3.exe\"
                        taskkill /f /im \"VALORANT-Win64-Shipping.exe\"
                        taskkill /f /im \"r5apex.exe\"
                        taskkill /f /im \"LeagueClient.exe\"
                        taskkill /f /im \"League of Legends.exe\"
                        taskkill /f /im \"browser_x86.exe\"
                        taskkill /f /im \"Heaven.exe\"
                        taskkill /f /im \"Valley.exe\"
                        taskkill /f /im \"launcher.exe\"
                        taskkill /f /im \"superposition.exe\"
                    """
                } else if (gameName == "Borderlands3") {
                    bat """
                        taskkill /f /im \"borderlands3.exe\"
                    """
                } else if (gameName == "Valorant") {
                    bat """
                        taskkill /f /im \"VALORANT-Win64-Shipping.exe\"
                    """
                } else if (gameName == "ApexLegends") {
                    bat """
                        taskkill /f /im \"r5apex.exe\"
                    """
                } else if (gameName == "LoL") {
                    bat """
                        taskkill /f /im \"LeagueClient.exe\"
                        taskkill /f /im \"League of Legends.exe\"
                    """
                } else if (gameName == "HeavenDX9" || gameName == "HeavenDX11") {
                    bat """
                        taskkill /f /im \"browser_x86.exe\"
                        taskkill /f /im \"Heaven.exe\"
                    """
                } else if (gameName == "ValleyDX9" || gameName == "ValleyDX11") {
                    bat """
                        taskkill /f /im \"browser_x86.exe\"
                        taskkill /f /im \"Valley.exe\"
                    """
                } else if (gameName == "Superposition") {
                    bat """
                        taskkill /f /im \"launcher.exe\"
                        taskkill /f /im \"superposition.exe\"
                    """
                }

                break
            case "OSX":
                println("Unsupported OS")
                break
            default:
                println("Unsupported OS")
        }
    } catch (e) {
        println("[ERROR] Failed to close games")
        println(e)
    }
}


def executeTestCommand(String osName, String asicName, Map options, String executionType) {
    String testsNames = options.tests
    String testsPackageName = options.testsPackage
    if (options.testsPackage != "none" && !options.isPackageSplitted) {
        if (testsNames.contains(".json")) {
            // if tests package isn't splitted and it's execution of this package - replace test package by test group and test group by empty string
            testsPackageName = options.tests
            testsNames = ""
        } else {
            // if tests package isn't splitted and it isn't execution of this package - replace tests package by empty string
            testsPackageName = ""
        }
    }

    String collectTraces = "False"

    if ((executionType == "server" && options.serverCollectTraces) || (executionType == "client" && options.clientCollectTraces)) {
        collectTraces = "True"
    }

    def screenResolution = "${options.clientInfo.screenWidth}x${options.clientInfo.screenHeight}"

    dir("scripts") {
        switch (osName) {
            case "Windows":
                bat """
                    run.bat \"${testsPackageName}\" \"${testsNames}\" \"${executionType}\" \"${options.serverInfo.ipAddress}\" \"${options.serverInfo.communicationPort}\" ${options.testCaseRetries} \"${options.serverInfo.gpuName}\" \"${options.serverInfo.osName}\" \"${options.engine}\" ${collectTraces} ${screenResolution} 1>> \"../${options.stageName}_${options.currentTry}_${executionType}.log\"  2>&1
                """

                break

            case "OSX":
                println "OSX isn't supported"
                break

            default:
                println "Linux isn't supported"
        }
    }
}


def saveResults(String osName, Map options, String executionType, Boolean stashResults, Boolean executeTestsFinished) {
    try {
        dir(options.stageName) {
            utils.moveFiles(this, osName, "../*.log", ".")
            utils.moveFiles(this, osName, "../scripts/*.log", ".")
            utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}_${executionType}.log")
        }

        archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true

        if (stashResults) {
            dir("Work") {
                if (fileExists("Results/StreamingSDK/session_report.json")) {

                    if (executionType == "client") {
                        def sessionReport = readJSON file: "Results/StreamingSDK/session_report.json"

                        if (sessionReport.summary.error > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "action_required", options, NotificationConfiguration.SOME_TESTS_ERRORED, "${BUILD_URL}")
                        } else if (sessionReport.summary.failed > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_FAILED, "${BUILD_URL}")
                        } else {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", options, NotificationConfiguration.ALL_TESTS_PASSED, "${BUILD_URL}")
                        }

                        println "Stashing all test results to : ${options.testResultsName}_client"
                        makeStash(includes: '**/*', name: "${options.testResultsName}_client", allowEmpty: true, storeOnNAS: options.storeOnNAS)

                        // reallocate node if there are still attempts
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped) {
                                collectCrashInfo(osName, options, options.currentTry)
                                String errorMessage = (options.currentTry < options.nodeReallocateTries) ? "All tests were marked as error. The test group will be restarted." : "All tests were marked as error."
                                throw new ExpectedExceptionWrapper(errorMessage, new Exception(errorMessage))
                            }
                        }
                    } else {
                        println "Stashing logs to : ${options.testResultsName}_server"
                        makeStash(includes: '**/*_server.log', name: "${options.testResultsName}_server_logs", allowEmpty: true, storeOnNAS: options.storeOnNAS)
                        makeStash(includes: '**/*.json', name: "${options.testResultsName}_server", allowEmpty: true, storeOnNAS: options.storeOnNAS)
                        makeStash(includes: '**/*_server.zip', name: "${options.testResultsName}_server_traces", allowEmpty: true, storeOnNAS: options.storeOnNAS)
                    }
                }
            }
        }
    } catch(e) {
        if (executeTestsFinished) {
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


def executeTestsClient(String osName, String asicName, Map options) {
    Boolean stashResults = true

    try {

        utils.reboot(this, osName)

        timeout(time: "45", unit: "MINUTES") {
            cleanWS(osName)
            checkoutScm(branchName: options.testsBranch, repositoryUrl: TESTS_REPO)
        }

        timeout(time: "5", unit: "MINUTES") {
            dir("jobs_launcher/install"){
                bat """
                    install_pylibs.bat
                """
            }

            dir("scripts"){
                bat """
                    install_pylibs.bat
                """
            }

            dir("StreamingSDK") {
                prepareTool(osName, options)
            }
        }

        options["clientInfo"]["screenWidth"] = getClientScreenWidth(osName, options)
        println("[INFO] Screen width on client machine: ${options.clientInfo.screenWidth}")

        options["clientInfo"]["screenHeight"] = getClientScreenHeight(osName, options)
        println("[INFO] Screen height on client machine: ${options.clientInfo.screenHeight}")

        options["clientInfo"]["ready"] = true
        println("[INFO] Client is ready to run tests")

        while (!options["serverInfo"]["ready"]) {
            if (options["serverInfo"]["failed"]) {
                throw new Exception("Server was failed")
            }

            sleep(5)
        }

        println("Client is synchronized with state of server. Start tests")
        
        executeTestCommand(osName, asicName, options, "client")

        options["clientInfo"]["executeTestsFinished"] = true

    } catch (e) {
        options["clientInfo"]["ready"] = false
        options["clientInfo"]["failed"] = true
        options["clientInfo"]["exception"] = e

        if (options.currentTry < options.nodeReallocateTries - 1) {
            stashResults = false
        } else {
            currentBuild.result = "FAILURE"
        }

        println "[ERROR] Failed during tests on client"
        println "Exception: ${e.toString()}"
        println "Exception message: ${e.getMessage()}"
        println "Exception cause: ${e.getCause()}"
        println "Exception stack trace: ${e.getStackTrace()}"
    } finally {
        options["clientInfo"]["finished"] = true

        saveResults(osName, options, "client", stashResults, options["clientInfo"]["executeTestsFinished"])
    }
}


def executeTestsServer(String osName, String asicName, Map options) {
    Boolean stashResults = true

    try {

        utils.reboot(this, osName)

        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "45", unit: "MINUTES") {
                cleanWS(osName)
                checkoutScm(branchName: options.testsBranch, repositoryUrl: TESTS_REPO)
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.INSTALL_PLUGIN) {
            timeout(time: "5", unit: "MINUTES") {
                dir("jobs_launcher/install"){
                    bat """
                        install_pylibs.bat
                    """
                }

                dir("scripts"){
                    bat """
                        install_pylibs.bat
                    """
                }

                dir("StreamingSDK") {
                    prepareTool(osName, options)
                }
            }
        }

        options["serverInfo"]["ipAddress"] = getServerIpAddress(osName, options)
        println("[INFO] IPv4 address of server: ${options.serverInfo.ipAddress}")

        options["serverInfo"]["gpuName"] = getGPUName()
        println("[INFO] Name of GPU on server machine: ${options.serverInfo.gpuName}")
        
        options["serverInfo"]["osName"] = getOSName()
        println("[INFO] Name of OS on server machine: ${options.serverInfo.osName}")

        options["serverInfo"]["communicationPort"] = getCommunicationPort(osName, options)
        println("[INFO] Communication port: ${options.serverInfo.communicationPort}")
        
        options["serverInfo"]["ready"] = true
        println("[INFO] Server is ready to run tests")

        while (!options["clientInfo"]["ready"]) {
            if (options["clientInfo"]["failed"]) {
                throw new Exception("Client was failed")
            }

            sleep(5)
        }

        println("Server is synchronized with state of client. Start tests")

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
            executeTestCommand(osName, asicName, options, "server")
        }

        options["serverInfo"]["executeTestsFinished"] = true

    } catch (e) {
        options["serverInfo"]["ready"] = false
        options["serverInfo"]["failed"] = true
        options["serverInfo"]["exception"] = e

        if (options.currentTry < options.nodeReallocateTries - 1) {
            stashResults = false
        } else {
            currentBuild.result = "FAILURE"
        }

        println "[ERROR] Failed during tests on server"
        println "Exception: ${e.toString()}"
        println "Exception message: ${e.getMessage()}"
        println "Exception cause: ${e.getCause()}"
        println "Exception stack trace: ${e.getStackTrace()}"
    } finally {
        options["serverInfo"]["finished"] = true

        saveResults(osName, options, "server", stashResults, options["serverInfo"]["executeTestsFinished"])

        closeGames(osName, options, options.engine)
    }
}


def executeTests(String osName, String asicName, Map options) {
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        options["clientInfo"] = new ConcurrentHashMap()
        options["serverInfo"] = new ConcurrentHashMap()

        println("[INFO] Start Client and Server processes for ${asicName}-${osName}")
        // create client and server threads and run them parallel
        Map threads = [:]

        threads["${options.stageName}-client"] = { 
            node(getClientLabels(options)) {
                timeout(time: options.TEST_TIMEOUT, unit: "MINUTES") {
                    ws("WS/${options.PRJ_NAME}_Test") {
                        executeTestsClient(osName, asicName, options)
                    }
                }
            }
        }

        threads["${options.stageName}-server"] = { executeTestsServer(osName, asicName, options) }

        parallel threads

        if (options["serverInfo"]["failed"]) {
            def exception = options["serverInfo"]["exception"]
            throw new ExpectedExceptionWrapper("Server side tests got an error: ${exception.getMessage()}", exception)
        } else if (options["clientInfo"]["failed"]) {
            def exception = options["clientInfo"]["exception"]
            throw new ExpectedExceptionWrapper("Client side tests got an error: ${exception.getMessage()}", exception)
        }
    } catch (e) {
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${e.getMessage()}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${e.getMessage()}", e.getCause())
        } else {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED}", e)
        }
    }
}


def executeBuildWindows(Map options) {
    utils.reboot(this, "Windows")

    options.winBuildConfiguration.each() { winBuildConf ->
        options.winVisualStudioVersion.each() { winVSVersion ->

            println "Current build configuration: ${winBuildConf}."
            println "Current VS version: ${winVSVersion}."

            String winBuildName = "${winBuildConf}_vs${winVSVersion}"
            String logName = "${STAGE_NAME}.${winBuildName}.log"

            String msBuildPath = ""
            String buildSln = ""
            String winArtifactsDir = "vs${winVSVersion}x64${winBuildConf.substring(0, 1).toUpperCase() + winBuildConf.substring(1).toLowerCase()}"

            switch(winVSVersion) {
                case "2017":
                    buildSln = "${AMF_SOLUTION}_vs2017.sln"

                    msBuildPath = bat(script: "echo %VS2017_PATH%",returnStdout: true).split('\r\n')[2].trim()
                    
                    break
                case "2019":
                    buildSln = "${AMF_SOLUTION}_vs2019.sln"

                    msBuildPath = bat(script: "echo %VS2019_PATH%",returnStdout: true).split('\r\n')[2].trim()

                    break
                default:
                    throw Exception("Unsupported VS version")
            }

            dir("StreamingSDK\\${AMF_SOLUTION_DIR}") {
                GithubNotificator.updateStatus("Build", "Windows", "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/${logName}")
                //coying over the ffmpeg file
                bat """
                    echo Copying ffmpeg files...
                    cd ../../../../../
                    set Luxoft_Dir=%CD%
                    cd ${AMF_BOOTSTRAP_REPO}
                    git checkout amd/stg/amf
                    git pull
                    if NOT %ERRORLEVEL% EQU 0 git pull
                    if NOT %ERRORLEVEL% EQU 0 echo WARNING: Issue with bootstrap repo
                    rd /q /s %Luxoft_Dir%\\${AMF_THIRDPARTY}\\ffmpeg
                    robocopy ${AMF_BOOTSTRAP_REPO}\\${AMF_THIRDPARTY}\\ffmpeg %Luxoft_Dir%\\${AMF_THIRDPARTY}\\ffmpeg /E /log:C:\\JN\\ffmpegcopy.log
                    if %ERRORLEVEL% LSS 4 set /a ERRORLEVEL=0

                    echo Building Project...
                    cd %Luxoft_Dir%\\${AMF_SOLUTION_DIR}
                    set msbuild="${msBuildPath}"
                    %msbuild% ${buildSln} /target:build /maxcpucount /nodeReuse:false /property:Configuration=${winBuildConf};Platform=x64 >> ..\\..\\..\\..\\${logName} 2>&1

                    set package_dir=%Luxoft_Dir%\\${BUILD_PACKAGE_PATH}
                    echo Making AMF public archives...
                    if exist %package_dir% cd %package_dir% && packageStreaming_SDK_Binaries.bat /nobuild
                """
            }

            String archiveUrl = ""

            dir("StreamingSDK\\${BASE_PROJECT_DIR}\\bin\\${winArtifactsDir}") {
                String BUILD_NAME = "StreamingSDK_Windows_${winBuildName}.zip"

                zip archive: true, zipFile: BUILD_NAME

                if (options.winTestingBuildName == winBuildName) {
                    utils.moveFiles(this, "Windows", BUILD_NAME, "${options.winTestingBuildName}.zip")
                    makeStash(includes: "${options.winTestingBuildName}.zip", name: "ToolWindows", preZip: false, storeOnNAS: options.storeOnNAS)
                }

                archiveUrl = "${BUILD_URL}artifact/${BUILD_NAME}"
                rtp nullAction: "1", parserName: "HTML", stableText: """<h3><a href="${archiveUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            }

        }
    }

    GithubNotificator.updateStatus("Build", "Windows", "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE)
}


def executeBuildAndroid(Map options) {
    utils.reboot(this, "Windows")

    options.androidBuildConfiguration.each() { androidBuildConf ->

        println "Current build configuration: ${androidBuildConf}."

        String androidBuildName = "${androidBuildConf}"
        String logName = "${STAGE_NAME}.${androidBuildName}.log"

        String androidBuildKeys = "assemble${androidBuildConf.substring(0, 1).toUpperCase() + androidBuildConf.substring(1).toLowerCase()}"

        dir("StreamingSDK/amf/protected/samples/CPPSamples/RemoteGameClientAndroid") {
            GithubNotificator.updateStatus("Build", "Android", "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/${logName}")

            bat """
                gradlew.bat ${androidBuildKeys} >> ..\\..\\..\\..\\..\\..\\${logName} 2>&1
            """

            String archiveUrl = ""

            dir("app/build/outputs/apk/arm/${androidBuildConf}") {
                String BUILD_NAME = "StreamingSDK_Android_${androidBuildName}.zip"

                zip archive: true, zipFile: BUILD_NAME, glob: "app-arm-${androidBuildConf}.apk"

                archiveUrl = "${BUILD_URL}artifact/${BUILD_NAME}"
                rtp nullAction: "1", parserName: "HTML", stableText: """<h3><a href="${archiveUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            }
        }

    }

    GithubNotificator.updateStatus("Build", "Android", "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE)
}


def executeBuild(String osName, Map options) {
    try {
        dir("StreamingSDK") {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, SparseCheckoutPaths: SPARSE_CHECKOUT_PATH)
            }
        }

        utils.removeFile(this, osName != "Android" ? osName : "Windows", "*.log")

        outputEnvironmentInfo(osName != "Android" ? osName : "Windows")

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(options)
                    break
                case "Android":
                    executeBuildAndroid(options)
                    break
                case "OSX":
                    println("Unsupported OS")
                    break
                default:
                    println("Unsupported OS")
            }
        }
    } catch (e) {
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}


def executePreBuild(Map options) {
    // manual job
    if (!env.BRANCH_NAME) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        options.executeBuild = true
        options.executeTests = true

        options.tests = "Smoke"

        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
        } else if("${env.BRANCH_NAME}" == "master") {
            println "[INFO] master branch was detected"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
        }
    }

    if ("StreamingSDK") {
        checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, disableSubmodules: true, SparseCheckoutPaths: SPARSE_CHECKOUT_PATH)
    }

    if (options.projectBranch) {
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
    options.commitSHA = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    options.commitShortSHA = options.commitSHA[0..6]

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    println "Commit shortSHA: ${options.commitShortSHA}"

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    def tests = []

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir("jobs_test_streaming_sdk") {
            checkoutScm(branchName: options.testsBranch, repositoryUrl: TESTS_REPO)
            options["testsBranch"] = utils.getBatOutput(this, "git log --format=%%H -1 ")
            dir('jobs_launcher') {
                options['jobsLauncherBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
            }
            println "[INFO] Test branch hash: ${options['testsBranch']}"

            def packageInfo

            if (options.testsPackage != "none") {
                packageInfo = readJSON file: "jobs/${options.testsPackage}"
                options.isPackageSplitted = packageInfo["split"]
                // if it's build of manual job and package can be splitted - use list of tests which was specified in params (user can change list of tests before run build)
                if (!env.BRANCH_NAME && options.isPackageSplitted && options.tests) {
                    options.testsPackage = "none"
                }
            }

            if (options.testsPackage != "none") {
                if (options.isPackageSplitted) {
                    println "[INFO] Tests package '${options.testsPackage}' can be splitted"
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tests = options.tests.split(" ") as List
                    }
                    println "[INFO] Tests package '${options.testsPackage}' can't be splitted"
                }
                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"

                // receive list of group names from package
                List groupsFromPackage = []

                if (packageInfo["groups"] instanceof Map) {
                    groupsFromPackage = packageInfo["groups"].keySet() as List
                } else {
                    // iterate through all parts of package
                    packageInfo["groups"].each() {
                        groupsFromPackage.addAll(it.keySet() as List)
                    }
                }

                groupsFromPackage.each {
                    if (options.isPackageSplitted) {
                        tests << it
                    } else {
                        if (tests.contains(it)) {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it}"
                        }
                    }
                }
                options.tests = utils.uniteSuites(this, "jobs/weights.json", tests)
                modifiedPackageName = modifiedPackageName.replace('~,', '~')

                if (options.isPackageSplitted) {
                    options.testsPackage = "none"
                } else {
                    options.testsPackage = modifiedPackageName
                    // check that package is splitted to parts or not
                    if (packageInfo["groups"] instanceof Map) {
                        tests << "${modifiedPackageName}"
                    } else {
                        // add group stub for each part of package
                        for (int i = 0; i < packageInfo["groups"].size(); i++) {
                            tests << "${modifiedPackageName}".replace(".json", ".${i}.json")
                            options.timeouts[options.testsPackage.replace(".json", ".${i}.json")] = options.NON_SPLITTED_PACKAGE_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT
                        }
                    }
                }

                options.tests = tests.join(" ")
            }
        }

        // clear games list if there isn't any test group in build or games string is empty
        if (!options.tests || !options.games) {
            options.engines = []
        }

        // launch tests for each game separately
        options.testsList = options.engines

        if (!options.tests && options.testsPackage == "none") {
            options.executeTests = false
        }

        if (env.BRANCH_NAME && options.githubNotificator) {
            options.githubNotificator.initChecks(options, "${BUILD_URL}")
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList, String game) {
    try {

        if (options["executeTests"] && testResultList) {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkoutScm(branchName: options.testsBranch, repositoryUrl: TESTS_REPO)
            }

            List lostStashes = []
            dir("summaryTestResults") {
                unstashCrashInfo(options["nodeRetry"])
                testResultList.each {
                    Boolean groupLost = false

                    if (it.endsWith(game)) {
                        List testNameParts = it.split("-") as List
                        String testName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                        dir(testName.replace("testResult-", "")) {
                            try {
                                makeUnstash(name: "${it}_server_logs", storeOnNAS: options.storeOnNAS)
                            } catch (e) {
                                println """
                                    [ERROR] Failed to unstash ${it}_server_logs
                                    ${e.toString()}
                                """

                                groupLost = true
                            }

                            try {
                                makeUnstash(name: "${it}_client", storeOnNAS: options.storeOnNAS)
                            } catch (e) {
                                println """
                                    [ERROR] Failed to unstash ${it}_client
                                    ${e.toString()}
                                """

                                groupLost = true
                            }

                            try {
                                makeUnstash(name: "${it}_server_traces", storeOnNAS: options.storeOnNAS)
                            } catch (e) {
                                println """
                                    [ERROR] Failed to unstash ${it}_server_traces
                                    ${e.toString()}
                                """
                            }

                            if (groupLost) {
                                lostStashes << ("'${it}'".replace("testResult-", ""))
                            }
                        }
                    }
                }
            }

            dir("serverTestResults") {
                testResultList.each {
                    if (it.endsWith(game)) {
                        List testNameParts = it.split("-") as List
                        String testName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                        dir(testName.replace("testResult-", "")) {
                            try {
                                makeUnstash(name: "${it}_server", storeOnNAS: options.storeOnNAS)
                            } catch (e) {
                                println """
                                    [ERROR] Failed to unstash ${it}_server
                                    ${e.toString()}
                                """
                            }
                        }
                    }
                }
            }

            try {
                dir ("scripts") {
                    python3("unite_case_results.py --target_dir \"..\\summaryTestResults\" --source_dir \"..\\serverTestResults\"")
                }
            } catch (e) {
                println "[ERROR] Can't unite server and client test results"
            }

            try {
                dir("jobs_launcher") {
                    bat """
                        count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests.toString()}\" \"\" \"{}\"
                    """
                }
            } catch (e) {
                println "[ERROR] Can't generate number of lost tests"
            }

            String branchName = env.BRANCH_NAME ?: options.projectBranch
            try {
                Boolean showGPUViewTraces = options.clientCollectTraces || options.serverCollectTraces

                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}", "SHOW_GPUVIEW_TRACES=${showGPUViewTraces}"]) {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            writeJSON file: "retry_info.json", json: JSONSerializer.toJSON(retryInfo, new JsonConfig()), pretty: 4
                        }

                        bat """
                            build_reports.bat ..\\summaryTestResults "StreamingSDK" ${options.commitSHA} ${branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\" \"${utils.escapeCharsByUnicode(game)}\"
                        """
                    }
                }
            } catch (e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                if (utils.isReportFailCritical(e.getMessage())) {
                    options.problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                    println """
                        [ERROR] Failed to build test report.
                        ${e.toString()}
                    """
                    if (!options.testDataSaved) {
                        try {
                            // Save test data for access it manually anyway
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, compare_report.html", \
                                "Test Report ${game}", "Summary Report, Compare Report", options.storeOnNAS, \
                                ["jenkinsBuildUrl": BUILD_URL, "jenkinsBuildName": currentBuild.displayName])
                            options.testDataSaved = true 
                        } catch (e1) {
                            println """
                                [WARNING] Failed to publish test data.
                                ${e.toString()}
                            """
                        }
                    }
                    throw e
                } else {
                    currentBuild.result = "FAILURE"
                    options.problemMessageManager.saveGlobalFailReason(errorMessage)
                }
            }

            try {
                dir("jobs_launcher") {
                    bat """
                        get_status.bat ..\\summaryTestResults
                    """
                }
            } catch (e) {
                println """
                    [ERROR] during slack status generation.
                    ${e.toString()}
                """
            }

            try {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            } catch(e) {
                println """
                    [ERROR] during archiving launcher.engine.log
                    ${e.toString()}
                """
            }

            Map summaryTestResults = [:]
            try {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                summaryTestResults = [passed: summaryReport.passed, failed: summaryReport.failed, error: summaryReport.error]
                if (summaryReport.error > 0) {
                    println "[INFO] Some tests marked as error. Build result = FAILURE."
                    currentBuild.result = "FAILURE"
                    options.problemMessageManager.saveGlobalFailReason(NotificationConfiguration.SOME_TESTS_ERRORED)
                } else if (summaryReport.failed > 0) {
                    println "[INFO] Some tests marked as failed. Build result = UNSTABLE."
                    currentBuild.result = "UNSTABLE"
                    options.problemMessageManager.saveUnstableReason(NotificationConfiguration.SOME_TESTS_FAILED)
                }
            } catch(e) {
                println """
                    [ERROR] CAN'T GET TESTS STATUS
                    ${e.toString()}
                """
                options.problemMessageManager.saveUnstableReason(NotificationConfiguration.CAN_NOT_GET_TESTS_STATUS)
                currentBuild.result = "UNSTABLE"
            }

            try {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            } catch (e) {
                println e.toString()
                options.testsStatus = ""
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.PUBLISH_REPORT) {
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, compare_report.html", \
                    "Test Report ${game}", "Summary Report, Compare Report", options.storeOnNAS, \
                    ["jenkinsBuildUrl": BUILD_URL, "jenkinsBuildName": currentBuild.displayName])

                if (summaryTestResults) {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options,
                            "${NotificationConfiguration.REPORT_PUBLISHED} Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options,
                            NotificationConfiguration.REPORT_PUBLISHED, "${BUILD_URL}/Test_20Report")
                }
            }
        }
    } catch (e) {
        println(e.toString())
        throw e
    }
}


def call(String projectBranch = LUXSDK_AUTOJOB_CONFIG['projectBranch'],
    String testsBranch = LUXSDK_AUTOJOB_CONFIG['testsBranch'],
    String platforms = LUXSDK_AUTOJOB_CONFIG['platforms'],
    String clientTag = LUXSDK_AUTOJOB_CONFIG['clientTag'],
    String winBuildConfiguration = LUXSDK_AUTOJOB_CONFIG['winBuildConfiguration'],
    String winVisualStudioVersion = LUXSDK_AUTOJOB_CONFIG['winVisualStudioVersion'],
    String winTestingBuildName = LUXSDK_AUTOJOB_CONFIG['winTestingBuildName'],
    String testsPackage = LUXSDK_AUTOJOB_CONFIG['testsPackage'],
    String tests = LUXSDK_AUTOJOB_CONFIG['tests'],
    String testerTag = LUXSDK_AUTOJOB_CONFIG['testerTag'],
    Integer testCaseRetries = LUXSDK_AUTOJOB_CONFIG['testCaseRetries'],
    Boolean clientCollectTraces = LUXSDK_AUTOJOB_CONFIG['clientCollectTraces'],
    Boolean serverCollectTraces = LUXSDK_AUTOJOB_CONFIG['serverCollectTraces'],
    String games = LUXSDK_AUTOJOB_CONFIG['games'],
    String androidBuildConfiguration = LUXSDK_AUTOJOB_CONFIG['androidBuildConfiguration'],
    Boolean storeOnNAS = LUXSDK_AUTOJOB_CONFIG['storeOnNAS']
    )
{
    print('IN SCRIPT')
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager

    def nodeRetry = []

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            gpusCount = 0
            platforms.split(';').each() { platform ->
                List tokens = platform.tokenize(':')
                if (tokens.size() > 1) {
                    gpuNames = tokens.get(1)
                    gpuNames.split(',').each() {
                        gpusCount += 1
                    }
                }
            }

            println """
                Platforms: ${platforms}
                Tests: ${tests}
                Tests package: ${testsPackage}
            """

            winBuildConfiguration = winBuildConfiguration.split(',')
            winVisualStudioVersion = winVisualStudioVersion.split(',')

            println """
                Win build configuration: ${winBuildConfiguration}"
                Win visual studio version: ${winVisualStudioVersion}"
                Win testing build name: ${winTestingBuildName}
            """

            androidBuildConfiguration = androidBuildConfiguration.split(',')

            println """
                Android build configuration: ${androidBuildConfiguration}"
            """

            Integer testTimeout = (clientCollectTraces || serverCollectTraces) ? 600 : 420

            println """
                Test stage timeout: ${testTimeout}
            """

            options << [projectRepo: PROJECT_REPO,
                        projectBranch: projectBranch,
                        testsBranch: testsBranch,
                        enableNotifications: false,
                        testsPackage:testsPackage,
                        tests:tests,
                        PRJ_NAME: "StreamingSDK",
                        splitTestsExecution: false,
                        winBuildConfiguration: winBuildConfiguration,
                        winVisualStudioVersion: winVisualStudioVersion,
                        winTestingBuildName: winTestingBuildName,
                        androidBuildConfiguration: androidBuildConfiguration,
                        gpusCount: gpusCount,
                        nodeRetry: nodeRetry,
                        platforms: platforms,
                        clientTag: clientTag,
                        BUILD_TIMEOUT: 50,
                        // update timeouts dynamicly based on number of cases + traces are generated or not
                        TEST_TIMEOUT: testTimeout,
                        DEPLOY_TIMEOUT: 90,
                        PREBUILD_TIMEOUT: 60,
                        BUILD_TIMEOUT: 60,
                        ADDITIONAL_XML_TIMEOUT: 15,
                        BUILDER_TAG: "BuilderStreamingSDK",
                        TESTER_TAG: testerTag,
                        CLIENT_TAG: "StreamingSDKClient && (${clientTag})",
                        testsPreCondition: this.&isIdleClient,
                        testCaseRetries: testCaseRetries,
                        engines: games.split(",") as List,
                        games: games,
                        clientCollectTraces:clientCollectTraces,
                        serverCollectTraces:serverCollectTraces,
                        storeOnNAS: storeOnNAS
                        ]
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
