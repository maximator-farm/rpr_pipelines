def getWindowsPluginName(Map options, Boolean isBuild) {
    String branch_postfix = ""
    if(env.BRANCH_NAME && BRANCH_NAME != "master") {
        branch_postfix = BRANCH_NAME.replace('/', '-')
    }
    if(env.Branch && Branch != "master") {
        branch_postfix = Branch.replace('/', '-')
    }
    if(branch_postfix && isBuild) {
        bat """
            rename RadeonProRender*zip *.(${branch_postfix}).zip
        """
    }

    return branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_Windows.(${branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_Windows.zip"
}

def executeBuildWindows(Map options) {
    dir('RadeonProRenderBlenderAddon\\BlenderPkg') {
        bat """
            build_win.cmd >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build') { 
            bat """
                rename rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_Windows.zip
            """
            
            String BUILD_NAME = getWindowsPluginName(options, true)
            archiveArtifacts "RadeonProRender*.zip"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            options['WindowsPluginName'] = BUILD_NAME
        }      
    }
}

def getOSXPluginName(Map options, Boolean isBuild) {
    String branch_postfix = ""
    if(env.BRANCH_NAME && BRANCH_NAME != "master") {
        branch_postfix = BRANCH_NAME.replace('/', '-')
    }
    if(env.Branch && Branch != "master") {
        branch_postfix = Branch.replace('/', '-')
    }
    if(branch_postfix && isBuild) {
        sh """
            for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${branch_postfix})\${i#\$name}"; done
        """
    }

    return branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_OSX.(${branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_OSX.zip"
}

def executeBuildOSX(Map options) {
    dir('RadeonProRenderBlenderAddon/BlenderPkg') {
        sh """
            ./build_osx.sh >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build') { 
            sh """
                mv rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_OSX.zip
            """

            String BUILD_NAME = getOSXPluginName(options, true)
            archiveArtifacts "RadeonProRender*.zip"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            
            options['OSXPluginName'] = BUILD_NAME
        }
    }
}

def getLinuxPluginName(Map options, Boolean isBuild) {
    String branch_postfix = ""
    if(env.BRANCH_NAME && BRANCH_NAME != "master") {
        branch_postfix = BRANCH_NAME.replace('/', '-')
    }
    if(env.Branch && Branch != "master") {
        branch_postfix = Branch.replace('/', '-')
    }
    if(branch_postfix && isBuild) {
        sh """
            for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${branch_postfix})\${i#\$name}"; done
        """
    }

    return branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_${osName}.(${branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_${osName}.zip"
}

def executeBuildLinux(String osName, Map options) {
    dir('RadeonProRenderBlenderAddon/BlenderPkg') {
        sh """
            ./build_linux.sh >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build') {

            sh """
                mv rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_${osName}.zip
            """

            String BUILD_NAME = getLinuxPluginName(options, true)
            archiveArtifacts "RadeonProRender*.zip"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            options['LinuxPluginName'] = BUILD_NAME
        }
        
    }
}

def executeBuild(String osName, Map options) {
    try {
        dir('RadeonProRenderBlenderAddon') {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderBlenderAddon.git')
        }
        
        switch(osName)  {
            case 'Windows':
                outputEnvironmentInfo(osName)
                executeBuildWindows(options);
                break;
            case 'OSX':
                if(!fileExists("python3")) {
                    sh "ln -s /usr/local/bin/python3.7 python3"
                }
                withEnv(["PATH=$WORKSPACE:$PATH"]) {
                    outputEnvironmentInfo(osName);
                    executeBuildOSX(options);
                 }
                break;
            default:
                if(!fileExists("python3")) {
                    sh "ln -s /usr/bin/python3.7 python3"
                }
                withEnv(["PATH=$PWD:$PATH"]) {
                    outputEnvironmentInfo(osName);
                    executeBuildLinux(osName, options);
                }
        }
    } catch (e) {
        println(e.toString());
        println(e.getMessage());
        options.failureMessage = "[ERROR] Failed to build plugin on ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options) {
    def tests = []
    if(options.testsPackage != "none") {
        dir('jobs_test_blender') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
            // json means custom test suite. Split doesn't supported
            if(options.testsPackage.endsWith('.json')) {
                def testsByJson = readJSON file: "jobs/${options.testsPackage}"
            }
            else {
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                }
                options.tests = tests
                options.testsPackage = "none"
            }
        }
    }
    else {
        options.tests.split(" ").each() {
            tests << "${it}"
        }
        options.tests = tests
    }

    if(!options.testsPackage.endsWith('.json')) {
        // if testsPackage wasn't specified or it isn't regression.json
        options.testsList = options.tests
    }
    else {
        // if testsPackage is regression.json
        options.testsList = ['']
        options.tests = tests.join(" ")
    }

    options['executeDeploy'] = true

    //if plugin is pre built
    if (options['isPreBuilt']) {
        options['executeBuild'] = false
        options['executeTests'] = true
        return;
    }

    currentBuild.description = ""
    ['projectBranch'].each {
        if(options[it] != 'master' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }

    dir('RadeonProRenderBlenderAddon') {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderBlenderAddon.git', true)

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
        options['commitShortSHA'] = options['commitSHA'][0..6]

        if(options['incrementVersion']) {
            if("${BRANCH_NAME}" == "master" && "${AUTHOR_NAME}" != "radeonprorender") {
                options.testsPackage = "regression.json"
                echo "Incrementing version of change made by ${AUTHOR_NAME}."

                String currentversion=version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ')
                echo "currentversion ${currentversion}"

                new_version=version_inc(currentversion, 3, ', ')
                echo "new_version ${new_version}"

                version_write("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', new_version, ', ')

                String updatedversion=version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ', "true")
                echo "updatedversion ${updatedversion}"

                bat """
                    git add src/rprblender/__init__.py
                    git commit -m "buildmaster: version update to ${updatedversion}"
                    git push origin HEAD:master
                   """

                //get commit's sha which have to be build
                options['projectBranch'] = bat ( script: "git log --format=%%H -1 ",
                                    returnStdout: true
                                    ).split('\r\n')[2].trim()

                options['executeBuild'] = true
                options['executeTests'] = true
            } else {
                options.testsPackage = "smoke"
                if(commitMessage.contains("CIS:BUILD")) {
                    options['executeBuild'] = true
                }

                if(commitMessage.contains("CIS:TESTS")) {
                    options['executeBuild'] = true
                    options['executeTests'] = true
                }

                if (env.CHANGE_URL) {
                    echo "branch was detected as Pull Request"
                    options['executeBuild'] = true
                    options['executeTests'] = true
                    options.testsPackage = "regression.json"
                }

                if("${BRANCH_NAME}" == "master") {
                   echo "rebuild master"
                   options['executeBuild'] = true
                   options['executeTests'] = true
                   options.testsPackage = "regression.json"
                }
            }
        }
        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ').replace(', ', '.')
    }
    if(env.CHANGE_URL) {
        //TODO: fix sha for PR
        //options.comitSHA = bat ( script: "git log --format=%%H HEAD~1 -1", returnStdout: true ).split('\r\n')[2].trim()
        options.AUTHOR_NAME = env.CHANGE_AUTHOR_DISPLAY_NAME
        if (env.CHANGE_TARGET != 'master') {
            options['executeBuild'] = false
            options['executeTests'] = false
            options['executeDeploy'] = false
        }
        options.commitMessage = env.CHANGE_TITLE
    }
    // if manual job
    if(options['forceBuild']) {
        options['executeBuild'] = true
        options['executeTests'] = true
    }
    // if rebuild or build report
    if(options['buildMode'] == 'Rebuild_Report' || options['buildMode'] == 'Build_Report') {
        options['executeBuild'] = false
        options['executeTests'] = false
    }
    // if only execute tests
    if(options['buildMode'] == 'Execute_Tests') {
        options['executeBuild'] = true
        options['executeTests'] = true
        options['executeDeploy'] = false
    }
    // if only delete tests
    if(options['buildMode'] == 'Delete_Tests') {
        options['executeBuild'] = false
        options['executeTests'] = false
        options['executeDeploy'] = false
    }

    currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
    if(!env.CHANGE_URL) {
        currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProRenderBlenderPlugin-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '50']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }
}

def executeDeploy(Map options, Map testsBuildsIds) {
    try {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')

        List lostArchive = []

        Integer previousBuildId = -1
        if (options['buildMode'] == 'Rebuild_Report') {
            // search id of previous build with the same global id for use its results as part of new report
            List builds = Jenkins.instance.getItem(env.JOB_NAME).getBuilds()
            for (build in builds) {
                String[] nameParts = build.getDisplayName().split('-')
                if (options.buildId == nameParts[nameParts.length - 1] && "${build.getNumber()}" != env.BUILD_NUMBER) {
                    previousBuildId = build.getNumber()
                    println("[INFO] Found master build with build id #${previousBuildId}")
                    break
                }
            }
            // it's necessary to avoid NotSerializableException (builds is a RunList object which isn't serializable)
            builds = null
            // if id of previous build with the same global id not found
            if (previousBuildId != -1) {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsUser', usernameVariable: 'USER', passwordVariable: 'PASSWORD']]) {
                    bat """
                        curl -o "${options.reportName}.zip" -u %USER%:%PASSWORD% "https://rpr.cis.luxoft.com/job/${env.JOB_NAME}/${previousBuildId}/${options.reportName}/*zip*/${options.reportName}"
                    """
                }
                unzip(zipFile: "${options.reportName}.zip", dir: ".")
                bat """
                rename ${options.reportName} summaryTestResults
                del /f ${options.reportName}.zip
                """

                dir("summaryTestResults") {
                    bat """
                    del /f *.html
                    del /f *.json
                    rd /s /q report_resources
                    """
                }

                // copy plugin for Windows from previous build
                try {
                    String BUILD_NAME = getWindowsPluginName(options, false)
                    copyArtifacts(filter: "${BUILD_NAME}", fingerprintArtifacts: false, projectName: "${env.JOB_NAME}", selector: specific("${previousBuildId}"))
                    archiveArtifacts BUILD_NAME
                    rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
                } catch (e) {
                    println("[INFO] Failed to copy Windows plugin artifact. Be sure that it isn't exist.")
                }
                // copy plugin for OSX from previous build
                try {
                    String BUILD_NAME = getOSXPluginName(options, false)
                    copyArtifacts(filter: "${BUILD_NAME}", fingerprintArtifacts: false, projectName: "${env.JOB_NAME}", selector: specific("${previousBuildId}"))
                    archiveArtifacts BUILD_NAME
                    rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
                } catch (e) {
                    println("[INFO] Failed to copy OSX plugin artifact. Be sure that it isn't exist.")
                }
                // copy plugin for Linux from previous build
                try {
                    String BUILD_NAME = getLinuxPluginName(options, false)
                    copyArtifacts(filter: "${BUILD_NAME}", fingerprintArtifacts: false, projectName: "${env.JOB_NAME}", selector: specific("${previousBuildId}"))
                    archiveArtifacts BUILD_NAME
                    rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
                } catch (e) {
                    println("[INFO] Failed to copy Linux plugin artifact. Be sure that it isn't exist.")
                }
            }
        }

        dir("summaryTestResults") {
            testsBuildsIds.each { key, value ->
                if (value == -1) {
                    //tests build terminated with 'FAILURE' or 'ABORTED' status
                    lostArchive.add("'${key}'")
                } else if (value != 0) {
                    String artifactName = "testResult-${key}.zip"
                    try {
                        println("Copy artifact with name ${artifactName}")
                        copyArtifacts(filter: "${artifactName}", fingerprintArtifacts: false, projectName: "${options.testsJobName}", selector: specific("${value}"))
                        unzip(zipFile: "${artifactName}", dir: "${key}-temp")
                        try {
                            bat """
                            rd /s /q ${key}
                            """
                        } catch(e) {
                        }
                        bat """
                        rename ${key}-temp ${key}
                        del /f ${artifactName}
                        """
                        try {
                            if (options['buildMode'] != 'Build_Report') {
                                // delete test build whose results were successfully received
                                jenkins.model.Jenkins.instance.getItem(options.testsJobName).getBuild("${value}").delete()
                            }
                        } catch (e) {
                            echo "[ERROR] Failed to delete test build with id #${value}"
                            println(e.toString());
                            println(e.getMessage());
                        }
                    } catch(e) {
                        echo "[ERROR] Failed to copy test results for ${key} from test build"
                        lostArchive.add("'${key}'")
                        println(e.toString());
                        println(e.getMessage());
                    }
                } else {
                    if (options['buildMode'] == 'Rebuild_Report') {
                        if (!fileExists(key)) {
                            echo "[ERROR] Failed to copy test results for ${key} from existing report"
                            lostArchive.add("'${key}'")
                        }
                    }
                }
            }
        }

        try {
            Boolean isRegression = options.testsPackage.endsWith('.json')

            dir("jobs_launcher") {
                bat """
                count_lost_tests.bat \"${lostArchive}\" .. ..\\summaryTestResults ${isRegression}
                """
            }
        } catch (e) {
            println("[ERROR] Can't generate number of lost tests")
        }

        String branchName = env.BRANCH_NAME ?: options.projectBranch
        try {
            withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"]) {
                dir("jobs_launcher") {
                    if (options['isPreBuilt']) {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('Blender 2.82')}" "PreBuilt" "PreBuilt" "PreBuilt"
                        """
                    } else {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('Blender 2.82')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            }
        } catch(e) {
            println("[ERROR] Failed to build report.")
            println(e.toString())
            println(e.getMessage())
        }

        try {
            dir("jobs_launcher") {
                bat "get_status.bat ..\\summaryTestResults"
            }
        } catch(e) {
            println("[ERROR] Failed to generate slack status.")
            println(e.toString())
            println(e.getMessage())
        }

        try {
            def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
            if (summaryReport.error > 0) {
                println("[INFO] Some tests marked as error. Build result = FAILED.")
                currentBuild.result="FAILED"
            } else if (summaryReport.failed > 0) {
                println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                currentBuild.result="UNSTABLE"
            }
        } catch(e) {
            println(e.toString())
            println(e.getMessage())
            println("CAN'T GET TESTS STATUS")
            currentBuild.result="UNSTABLE"
        }

        try {
            options.testsStatus = readFile("summaryTestResults/slack_status.json")
        } catch(e) {
            println(e.toString())
            println(e.getMessage())
            options.testsStatus = ""
        }

        publishHTML([allowMissing: false,
                     alwaysLinkToLastBuild: false,
                     keepAll: true,
                     reportDir: 'summaryTestResults',
                     reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
                     // TODO: custom reportName (issues with escaping)
                     reportName: 'Test Report',
                     reportTitles: 'Summary Report, Performance Report, Compare Report'])

        if (options['buildMode'] == 'Rebuild_Report' && previousBuildId != -1) {
            // delete previous master build whose report was successfully received
            jenkins.model.Jenkins.instance.getItem(env.JOB_NAME).getBuild("${previousBuildId}").delete()
        }

        println "BUILD RESULT: ${currentBuild.result}"
        println "BUILD CURRENT RESULT: ${currentBuild.currentResult}"
    }
    catch(e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}

def appendPlatform(String filteredPlatforms, String platform) {
    if (filteredPlatforms) {
        filteredPlatforms +=  ";" + platform
    } else {
        filteredPlatforms += platform
    }
    return filteredPlatforms
}


def call(String pipelinesBranch = "",
    String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    Boolean updateRefs = false,
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "",
    Boolean forceBuild = false,
    String resX = '0',
    String resY = '0',
    String SPU = '25',
    String iter = '50',
    String theshold = '0.05',
    String buildId = "",
    String buildMode = "",
    String additionalSettings = "",
    String customBuildLinkWindows = "",
    String customBuildLinkLinux = "",
    String customBuildLinkOSX = "") {

    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '25' : SPU
    iter = (iter == 'Default') ? '50' : iter
    theshold = (theshold == 'Default') ? '0.05' : theshold
    try {
        Boolean isPreBuilt = customBuildLinkWindows || customBuildLinkOSX || customBuildLinkLinux

        if (isPreBuilt) {
            //remove platforms for which pre built plugin is not specified
            String filteredPlatforms = ""

            platforms.split(';').each() { platform ->
                List tokens = platform.tokenize(':')
                String platformName = tokens.get(0)

                switch(platformName)  {
                case 'Windows':
                    if (customBuildLinkWindows) {
                        filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                    }
                    break;
                case 'OSX':
                    if (customBuildLinkOSX) {
                        filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                    }
                    break;
                default:
                    if (customBuildLinkLinux) {
                        filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                    }
                }
            }

            platforms = filteredPlatforms
        }

        String PRJ_NAME="RadeonProRenderBlender2.8Plugin_Rebuildable"
        String ASSETS_NAME="RadeonProRenderBlender2.8Plugin"
        String PRJ_ROOT="rpr-plugins"

        //String with labels which are common for all nodes of that job 
        String TESTER_TAG = "Blender2.8"
        String COMMON_LABELS = "${TESTER_TAG} && OpenCL"

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


        List additionalSettingsList = []
        additionalSettings.split(',').each() { setting ->
            additionalSettingsList.add(setting)
        }

        multiplatform_pipeline_rebuildable(platforms, this.&executePreBuild, this.&executeBuild, this.&executeDeploy,
                               [pipelinesBranch:pipelinesBranch,
                                projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                ASSETS_NAME:ASSETS_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests,
                                isPreBuilt:isPreBuilt,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:90,
                                DEPLOY_TIMEOUT:150,
                                COMMON_LABELS:COMMON_LABELS,
                                BUILDER_TAG:"BuildBlender2.8",
                                resX: resX,
                                resY: resY,
                                SPU: SPU,
                                iter: iter,
                                theshold: theshold,
                                testsJobName:"DevRadeonProRenderBlender2.8Tests",
                                buildId:buildId,
                                buildMode:buildMode,
                                additionalSettings:additionalSettingsList,
                                customBuildLinkWindows: customBuildLinkWindows,
                                customBuildLinkLinux: customBuildLinkLinux,
                                customBuildLinkOSX: customBuildLinkOSX
                                ])
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString())
        println(e.getMessage())

        throw e
    }

}