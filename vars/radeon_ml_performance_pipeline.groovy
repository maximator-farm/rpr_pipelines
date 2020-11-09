def executeTestCommand(String osName, String asicName, Map options) {
    timeout(time: options.TEST_TIMEOUT, unit: 'MINUTES') { 
        dir('scripts')
        {
            bat"""
            run.bat ${options.renderDevice} \"${options.testsPackage}\" \"${options.tests}\" >> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
            """
        }
    }
}


def executeTests(String osName, String asicName, Map options)
{
    try {
        timeout(time: "5", unit: 'MINUTES') {
            cleanWS(osName)
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ml.git')
            println("[INFO] Preparing on ${env.NODE_NAME} successfully finished.")
        }
    } catch (e) {
        if (utils.isTimeoutExceeded(e)) {
            println("Failed to download tests repository due to timeout.")
        } else {
            println("Failed to download tests repository.")
        }
        currentBuild.result = "FAILURE"
        throw e
    }

    try {
        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/MLAssets/", 'MLAssets')
    } catch (e) {
        println("Failed to download test scenes.")
        currentBuild.result = "FAILURE"
        throw e
    }

    try {
        outputEnvironmentInfo(osName, "${STAGE_NAME}.UnitTests")
        dir("RadeonML") {
            unstash "app${osName}"
        }

        executeTestCommand(osName, asicName, options)
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        // TODO implement stashing of results
    }
}


def executeWindowsBuildCommand(Map options, String build_type){

    bat """
        mkdir build-${build_type}
        cd build-${build_type}
        cmake ${options.cmakeKeysWin} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ..\\${STAGE_NAME}_${build_type}.log 2>&1
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
        %msbuild% RadeonML.sln -property:Configuration=${build_type} >> ..\\${STAGE_NAME}_${build_type}.log 2>&1
    """
    
    bat """
        cd build-${build_type}
        xcopy ..\\third_party\\miopen\\MIOpen.dll ${build_type}
        xcopy ..\\third_party\\tensorflow\\windows\\* ${build_type}
        mkdir ${build_type}\\rml
        mkdir ${build_type}\\rml_internal
        xcopy ..\\rml\\include\\rml\\*.h* ${build_type}\\rml
        xcopy ..\\rml\\include\\rml_internal\\*.h* ${build_type}\\rml_internal
    """

    zip dir: "build-${build_type}\\${build_type}", zipFile: "build-${build_type}\\${CIS_OS}_${build_type}.zip"
    archiveArtifacts "build-${build_type}\\${CIS_OS}_${build_type}.zip"
    
    zip archive: true, dir: "build-${build_type}\\${build_type}", glob: "RadeonML*.lib, RadeonML*.dll, MIOpen.dll, libtensorflow*, test*.exe", zipFile: "${CIS_OS}_${build_type}.zip"

}


def executeBuildWindows(Map options)
{
    bat """
        xcopy ..\\\\RML_thirdparty\\\\MIOpen third_party\\\\miopen /s/y/i
        xcopy ..\\\\RML_thirdparty\\\\tensorflow third_party\\\\tensorflow /s/y/i
    """

    options.cmakeKeysWin ='-G "Visual Studio 15 2017 Win64" -DRML_DIRECTML=ON -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'

    executeWindowsBuildCommand(options, "Release")
    executeWindowsBuildCommand(options, "Debug")

}


def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    }

    options.testsList = ['']
}


def executeBuild(String osName, Map options)
{
    String error_message = ""
    String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"

    try
    {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        receiveFiles("rpr-ml/MIOpen/${osName}/*", "../RML_thirdparty/MIOpen")
        receiveFiles("rpr-ml/tensorflow/*", "../RML_thirdparty/tensorflow")

        outputEnvironmentInfo(osName)

        withEnv(["CIS_OS=${osName}"]) {
            switch (osName) {
                case 'Windows':
                    executeBuildWindows(options);
                    break;
                case 'OSX':
                    print("[WARNING] ${osName} is not supported")
                    break;
                default:
                    print("[WARNING] ${osName} is not supported")
            }
        }

        dir('build-Release/Release') {
            stash includes: '*', name: "app${osName}"
        }
    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    //TODO implement deploy stage
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String testsPackage = "",
         String tests = "",
         String platforms = 'Windows:NVIDIA_RTX2080TI',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         Boolean enableNotifications = false)
{
    String PRJ_ROOT='rpr-ml'
    String PRJ_NAME='RadeonML'

    println "Platforms: ${platforms}"
    println "Tests: ${tests}"
    println "Tests package: ${testsPackage}"

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
            [platforms:platforms,
             projectBranch:projectBranch,
             testsBranch:testsBranch,
             enableNotifications:enableNotifications,
             PRJ_NAME:PRJ_NAME,
             PRJ_ROOT:PRJ_ROOT,
             projectRepo:projectRepo,
             testsPackage:testsPackage,
             tests:tests.replace(',', ' '),
             BUILDER_TAG:'BuilderML',
             TESTER_TAG:'MLPerf',
             BUILD_TIMEOUT:'30',
             TEST_TIMEOUT:'60',
             executeBuild:true,
             executeTests:true,
             retriesForTestStage:1])
}
