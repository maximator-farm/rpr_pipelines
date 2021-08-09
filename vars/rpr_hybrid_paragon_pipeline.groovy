def executeBuildWindows(Map options) {
    dir("ParagonGame") {
        withCredentials([string(credentialsId: "artNasIP", variable: 'ART_NAS_IP')]) {
            String paragonGameURL = "svn://" + ART_NAS_IP + "/ParagonGame"
            checkoutScm(checkoutClass: "SubversionSCM", repositoryUrl: paragonGameURL, credentialsId: "artNasUser")
        }
    }

    dir("RPRHybrid") {
        checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo)
    }

    dir("RPRHybrid-UE") {
        checkoutScm(branchName: options.ueBranch, repositoryUrl: options.ueRepo)
    }   
}


def executeBuild(String osName, Map options) {
    try {
        outputEnvironmentInfo(osName)
        
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            GithubNotificator.updateStatus("Build", osName, "in_progress", options, "Checkout has been finished. Trying to build...")

            switch(osName) {
                case "Windows":
                    executeBuildWindows(options)
                    break
                default:
                    println("${osName} is not supported")
            }
        }

        // TODO do stash
    } catch (e) {
        println(e.getMessage())
        throw e
    } finally {
        archiveArtifacts "*.log"
    }
}

def executePreBuild(Map options) {
    checkoutScm(branchName: options.projectBranch, repositoryUrl: options.projectRepo, disableSubmodules: true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true)
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    options.commitMessage = []
    commitMessage = commitMessage.split('\r\n')
    commitMessage[2..commitMessage.size()-1].collect(options.commitMessage) { it.trim() }
    options.commitMessage = options.commitMessage.join('\n')

    println "Commit list message: ${options.commitMessage}"
}


def call(String projectBranch = "",
         String ueBranch = "",
         String platforms = "Windows") {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            PRJ_NAME:"HybridParagon",
                            projectRepo:"git@github.com:Radeon-Pro/RPRHybrid.git",
                            ueRepo:"git@github.com:Radeon-Pro/RPRHybrid-UE.git",
                            BUILDER_TAG:"BuilderU",
                            TESTER_TAG:"HybridTester",
                            executeBuild:true,
                            executeTests:true,
                            retriesForTestStage:1,
                            storeOnNAS: true])
}
