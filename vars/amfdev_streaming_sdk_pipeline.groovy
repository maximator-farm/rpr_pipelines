import groovy.transform.Field


@Field final String PROJECT_REPO = "git@github.com:amfdev/StreamingSDK.git"


def executeBuildWindows(Map options) {
    options.buildConfiguration.each() { winBuildConf ->
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
                    buildSln = "StreamingSDK_vs2017.sln"
                    msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe"
                    break
                case "2019":
                    buildSln = "StreamingSDK_vs2019.sln"
                    msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe"

                    if (!fileExists(msBuildPath)) {
                        msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Current\\Bin\\MSBuild.exe"
                    }
                    break
                default:
                    throw Exception("Unsupported VS version")
            }

            dir("StreamingSDK\\amf\\protected\\samples") {
                GithubNotificator.updateStatus("Build", "Windows", "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/${logName}")

                bat """
                    set msbuild="${msBuildPath}"
                    %msbuild% ${buildSln} /target:build /maxcpucount /nodeReuse:false /property:Configuration=${winBuildConf};Platform=x64 >> ..\\..\\..\\..\\${logName} 2>&1
                """
            }

            String archiveUrl = ""

            dir("StreamingSDK\\amf\\bin\\${winArtifactsDir}") {
                String BUILD_NAME = "StreamingSDK_Windows_${winBuildName}.zip"

                zip archive: true, zipFile: BUILD_NAME

                if (options.winTestingBuildName == winBuildName) {
                    makeStash(includes: BUILD_NAME, name: BUILD_NAME)
                }

                archiveUrl = "${BUILD_URL}artifact/${BUILD_NAME}"
                rtp nullAction: "1", parserName: "HTML", stableText: """<h3><a href="${archiveUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
            }
        }
    }

    GithubNotificator.updateStatus("Build", "Windows", "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE)
}


def executeBuild(String osName, Map options) {
    try {
        dir("StreamingSDK") {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo)
            }
        }

        outputEnvironmentInfo(osName)

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(options)
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
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        options.executeBuild = true
        options.executeTests = true

        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
        } else if("${env.BRANCH_NAME}" == "master") {
            println "[INFO] master branch was detected"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
        }
    }

    if (!env.CHANGE_URL) {
        if ("StreamingSDK") {
            checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, disableSubmodules: true)
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
    }
}


def call(String projectBranch = "",
    String platforms = "Windows",
    String buildConfiguration = "release",
    String winVisualStudioVersion = "2017,2019",
    String winTestingBuildName = "release_vs2019",
    Boolean enableNotifications = true,
    String testerTag = "StreamingSDK"
    )
{

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

            buildConfiguration = buildConfiguration.split(',')
            winVisualStudioVersion = winVisualStudioVersion.split(',')

            println "Platforms: ${platforms}"

            println "Build configuration: ${buildConfiguration}"
            println "Win visual studio version: ${winVisualStudioVersion}"
            println "Win testing build name: ${winTestingBuildName}"

            options << [projectRepo: PROJECT_REPO,
                        projectBranch: projectBranch,
                        enableNotifications: enableNotifications,
                        PRJ_NAME: "StreamingSDK",
                        buildConfiguration: buildConfiguration,
                        winVisualStudioVersion: winVisualStudioVersion,
                        winTestingBuildName: winTestingBuildName,
                        gpusCount: gpusCount,
                        nodeRetry: nodeRetry,
                        platforms: platforms,
                        BUILD_TIMEOUT: 15,
                        BUILDER_TAG: "BuilderStreamingSDK",
                        TESTER_TAG: testerTag
                        ]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {
        problemMessageManager.publishMessages()
    }

}
