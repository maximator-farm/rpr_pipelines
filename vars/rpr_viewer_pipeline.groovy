
def getViewerTool(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appWindows"
                
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy RprViewer_Windows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" RprViewer_Windows.zip
                """
            }

            unzip zipFile: "RprViewer_Windows.zip", dir: "RprViewer", quiet: true

            break;

        case 'OSX':
            println "OSX isn't supported"
            break;

        default:
            
            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appUbuntu18"
                
                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp RprViewer_Ubuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """ 

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" RprViewer_Ubuntu18.zip
                """
            }

            unzip zipFile: "RprViewer_Ubuntu18.zip", dir: "RprViewer", quiet: true

            break;
    }
}


def executeGenTestRefCommand(String osName, Map options)
{
    try
    {
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e)
    {
        println("baseline_manifest.json not found")
    }

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_results_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_results_baseline.sh
                """
                break;
            default:
                sh """
                ./make_results_baseline.sh
                """
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
            run.bat ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
            """
        }
        break;

    case 'OSX':
        echo "OSX is not supported"
        break;

    default:
        dir('scripts')
        {
            withEnv(["LD_LIBRARY_PATH=../RprViewer/engines/hybrid:\$LD_LIBRARY_PATH"]) {
                sh """
                ./run.sh ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
                """
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {

        timeout(time: "10", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')
                if (options.sendToRBS) {
                    options.rbs_prod.setTester(options)
                }
                getViewerTool(osName, options)
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/Assets/", 'RprViewer')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName)

        if(options['updateRefs']) {
            executeTestCommand(osName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        } else {
            try {
                if(options.testsPackage != "none" && !options.testsPackage.endsWith('.json')) {
                    def tests = []
                    String tempTests = readFile("jobs/${options.testsPackage}")
                    tempTests.split("\n").each {
                        // TODO: fix: duck tape - error with line ending
                        tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                    }
                    options.tests = tests.join(" ")
                    options.testsPackage = "none"
                }
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } 
            catch (e) 
            {
                println("Baseline doesn't exist.")
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
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/RprViewer/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/RprViewer/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                    // reallocate node if there are still attempts
                    if (sessionReport.summary.total == sessionReport.summary.error) {
                        if (options.currentTry < options.nodeReallocateTries) {
                            throw new Exception("All tests crashed")
                        } 
                    }
                }
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    bat"""
        cmake . -B build -G "Visual Studio 15 2017" -A x64 >> ${STAGE_NAME}.log 2>&1
        cmake --build build --target RadeonProViewer --config Release >> ${STAGE_NAME}.log 2>&1

        mkdir ${options.DEPLOY_FOLDER}
        xcopy config.json ${options.DEPLOY_FOLDER}
        xcopy README.md ${options.DEPLOY_FOLDER}
        xcopy UIConfig.json ${options.DEPLOY_FOLDER}
        xcopy sky.hdr ${options.DEPLOY_FOLDER}
        xcopy build\\Viewer\\Release\\RadeonProViewer.exe ${options.DEPLOY_FOLDER}\\RadeonProViewer.exe*

        xcopy shaders ${options.DEPLOY_FOLDER}\\shaders /y/i/s

        mkdir ${options.DEPLOY_FOLDER}\\rpml\\lib
        xcopy rpml\\lib\\RadeonML-DirectML.dll ${options.DEPLOY_FOLDER}\\rpml\\lib\\RadeonML-DirectML.dll*
        xcopy rif\\models ${options.DEPLOY_FOLDER}\\rif\\models /s/i/y
        xcopy rif\\lib ${options.DEPLOY_FOLDER}\\rif\\lib /s/i/y
        del /q ${options.DEPLOY_FOLDER}\\rif\\lib\\*.lib
    """

    //temp fix
    bat"""
        xcopy build\\viewer\\engines ${options.DEPLOY_FOLDER}\\engines /s/i/y
    """

    def controlFiles = ['config.json', 'UIConfig.json', 'sky.hdr', 'RadeonProViewer.exe', 'rpml/lib/RadeonML-DirectML.dll']
        controlFiles.each() {
        if (!fileExists("${options.DEPLOY_FOLDER}/${it}")) {
            error "Not found ${it}"
        }
    }

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Windows.zip"
    stash includes: "RprViewer_Windows.zip", name: "appWindows"
    options.pluginWinSha = sha1 "RprViewer_Windows.zip"

}


def executeBuildLinux(Map options)
{
    sh """
        mkdir build
        cd build
        cmake .. >> ../${STAGE_NAME}.log 2>&1
        make >> ../${STAGE_NAME}.log 2>&1
    """

    sh """
        mkdir ${options.DEPLOY_FOLDER}
        cp config.json ${options.DEPLOY_FOLDER}
        cp README.md ${options.DEPLOY_FOLDER}
        cp UIConfig.json ${options.DEPLOY_FOLDER}
        cp sky.hdr ${options.DEPLOY_FOLDER}
        cp build/viewer/RadeonProViewer ${options.DEPLOY_FOLDER}/RadeonProViewer

        cp -rf shaders ${options.DEPLOY_FOLDER}/shaders

        mkdir ${options.DEPLOY_FOLDER}/rif
        cp -rf rif/models ${options.DEPLOY_FOLDER}/rif/models
        cp -rf rif/lib ${options.DEPLOY_FOLDER}/rif/lib

        cp -rf build/viewer/engines ${options.DEPLOY_FOLDER}/engines
    """

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Ubuntu18.zip"
    stash includes: "RprViewer_Ubuntu18.zip", name: "appUbuntu18"
    options.pluginUbuntuSha = sha1 "RprViewer_Ubuntu18.zip"
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows(options);
            break;
        case 'OSX':
            println "OSX isn't supported."
            break;
        default:
            executeBuildLinux(options);
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)

    AUTHOR_NAME = bat (
            script: "git show -s --format=%%an HEAD ",
            returnStdout: true
            ).split('\r\n')[2].trim()

    echo "The last commit was written by ${AUTHOR_NAME}."
    options.AUTHOR_NAME = AUTHOR_NAME

    commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true ).split('\r\n')[2].trim()
    echo "Commit message: ${commitMessage}"
    options.commitMessage = commitMessage

    options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    if (env.CHANGE_URL) {
        options.testsPackage = "PR"
    }
    else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    }
    else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '50']]]);
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try
    {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')

            List lostStashes = []

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
                            echo "[ERROR] Failed to unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
                Boolean isRegression = options.testsPackage.endsWith('.json')

                //dir("jobs_launcher") {
                //    bat """
                //    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults ${isRegression}
                //    """
                //}
                
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }
            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('RprViewer')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
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
                } else {
                    currentBuild.result="SUCCESS"
                }
            }
            catch(e)
            {
                println("CAN'T GET TESTS STATUS")
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
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report, Performance Report, Compare Report'])
        }
    }
    catch(e)
    {
        println(e.toString())
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII;Ubuntu18:AMD_RadeonVII',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "") {

    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='RadeonProViewer'
    String projectRepo='git@github.com:Radeon-Pro/RadeonProViewer.git'

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            testsBranch:testsBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            TESTER_TAG:'RprViewer',
                            executeBuild:true,
                            executeTests:true,
                            DEPLOY_FOLDER:"RprViewer",
                            testsPackage:testsPackage,
                            TEST_TIMEOUT:180,
                            tests:tests])
}
