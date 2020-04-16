

def getMayaPluginInstaller(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (options.pluginWinSha) {
                addon_name = options.pluginWinSha
            } else {
                addon_name = "unknown"
            }

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${addon_name}.msi")) {

                clearBinariesWin()
                
                println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                downloadPlugin(osName, "Maya", options)
                addon_name = options.pluginWinSha

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${addon_name}.msi"
                """

            } else {
                println "[INFO] The plugin ${addon_name}.msi exists in the storage."
            }

            break;

        default:
            echo "[WARNING] ${osName} is not supported"
    }

}

def executeGenTestRefCommand(String osName, Map options)
{
    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_rpr_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_rpr_baseline.sh
                """
                break;
            default:
                sh """
                ./make_rpr_baseline.sh
                """
        }
    }
}


def buildRenderCache(String osName, String toolVersion, String log_name)
{
    dir("scripts") {
        switch(osName) {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> ..\\${log_name}.cb.log  2>&1"
                break;
            default:
                echo "[WARNING] ${osName} is not supported"
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':
            dir('scripts')
            {
                bat """
                render_rpr.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
                """
            }
            break;
        default:
            echo "[WARNING] ${osName} is not supported"
    }
}


def executeTests(String osName, String asicName, Map options)
{
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {

        timeout(time: "5", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_vr2rpr_maya.git')
                dir('jobs/Scripts')
                {
                    unstash "convertionScript"
                }
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("/${options.PRJ_PATH}/VRay2MayaAssets/", 'VRay2MayaAssets')

        try {
            Boolean newPluginInstalled = false
            timeout(time: "15", unit: 'MINUTES') {
                getMayaPluginInstaller(osName, options)
                newPluginInstalled = installMSIPlugin(osName, 'Maya', options)
                println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
            }
            if (newPluginInstalled) {
                timeout(time: "5", unit: 'MINUTES') {
                    buildRenderCache(osName, options.toolVersion, options.stageName)
                    if(!fileExists("./Work/Results/vray2rpr/cache_building.jpg")){
                        println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                        throw new Exception("No output image during build cache")
                    }
                }
            }
        }
        catch(e) {
            println(e.toString())
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}.")
            // deinstalling broken addon
            installMSIPlugin(osName, "Maya", options, false, true)
            throw e
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        String REF_PATH_PROFILE_OR="${options.REF_PATH}/VRay-${osName}"
        String JOB_PATH_PROFILE_OR="${options.JOB_PATH}/VRay-${osName}"

        outputEnvironmentInfo(osName)

        if(options['updateORRefs'])
        {
            dir('scripts')
            {
                bat """
                render_or.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
                """
                bat "make_original_baseline.bat"
            }
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE_OR)
        }

        if(options['updateRefs'])
        {
            executeTestCommand(osName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }

        if (!options['updateORRefs'] && !options['updateRefs'])
        {	
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE_OR}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            executeTestCommand(osName, options)
        }

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        } 
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/vray2rpr/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/vray2rpr/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    // deinstalling broken addon
                    if (sessionReport.summary.total == sessionReport.summary.error) {
                        installMSIPlugin(osName, "Maya", options, false, true)
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true
                }
            }
        }
    }
}


def executePreBuild(Map options)
{

    dir('Vray2RPRConvertTool-Maya')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/Vray2RPRConvertTool-Maya.git')

        stash includes: "convertVR2RPR.py", name: "convertionScript"

        AUTHOR_NAME = bat (
                script: "git show -s --format=%%an HEAD ",
                returnStdout: true
                ).split('\r\n')[2].trim()

        echo "The last commit was written by ${AUTHOR_NAME}."
        options.AUTHOR_NAME = AUTHOR_NAME

        commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true )
        echo "Commit message: ${commitMessage}"

        options.commitMessage = commitMessage.split('\r\n')[2].trim()
        echo "Opt.: ${options.commitMessage}"
        options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    }


    def tests = []
    if(options.testsPackage != "none")
    {
        dir('jobs_test_vr2rpr_maya')
        {
            checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_vr2rpr_maya.git')
            // json means custom test suite. Split doesn't supported
            if(options.testsPackage.endsWith('.json'))
            {
                options.testsList = ['']
            }
            // options.splitTestsExecution = false
            String tempTests = readFile("jobs/${options.testsPackage}")
            tempTests.split("\n").each {
                // TODO: fix: duck tape - error with line ending
                tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
            }
            options.testsList = tests
            options.testsPackage = "none"
        }
    }
    else
    {
        options.tests.split(" ").each()
        {
            tests << "${it}"
        }
        options.testsList = tests
    }

}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_vr2rpr_maya.git')

            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "Can't unstash ${it}"
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults VRay2RPR ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                println("ERROR during report building")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("ERROR during slack status generation")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("Some tests crashed")
                    currentBuild.result="FAILED"
                }
                else if (summaryReport.failed > 0) {
                    println("Some tests failed")
                    currentBuild.result="UNSTABLE"
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("CAN'T GET TESTS STATUS")
                currentBuild.result="UNSTABLE"
            }

            try
            {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            }

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report'])
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}

def call(String customBuildLinkWindows = "",
         String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:NVIDIA_GF1080TI',
         Boolean updateORRefs = false,
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "",
         String toolVersion = "2020") {
    try
    {
        String PRJ_NAME="Vray2RPRConvertTool-Maya"
        String PRJ_ROOT="rpr-tools"

        multiplatform_pipeline(platforms, this.&executePreBuild, null, this.&executeTests, this.&executeDeploy,
                                [customBuildLinkWindows:customBuildLinkWindows,
                                projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateORRefs:updateORRefs,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                skipBuild:true,
                                isPreBuilt:true,
                                executeTests:true,
                                toolVersion:toolVersion,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                testsPackage:testsPackage,
                                tests:tests,
                                TESTER_TAG:"VRayMaya",
                                reportName:'Test_20Report',
                                TEST_TIMEOUT:120])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
