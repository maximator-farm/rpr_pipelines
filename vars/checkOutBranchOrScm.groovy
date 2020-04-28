import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import hudson.plugins.git.GitException
import hudson.AbortException

def call(String branchName, String repoName, Boolean disableSubmodules=false, Boolean polling=false, Boolean changelog=true, String credId='radeonprorender') {
    try {
        executeCheckout(branchName, repoName, disableSubmodules, polling, changelog, credId)
    } catch (FlowInterruptedException e) {
        println "[INFO] Task was aborted during checkout"
        throw e
    } catch (e) {
        println(e.toString())
        println(e.getMessage())

        println "[ERROR] Failed to checkout git on ${env.NODE_NAME}. Cleaning workspace and try again."
        cleanWS()
        executeCheckout(branchName, repoName, disableSubmodules, polling, changelog, credId)
    }
}


def executeCheckout(String branchName, String repoName, Boolean disableSubmodules=false, Boolean polling=false, Boolean changelog=true, String credId='radeonprorender') {
    if(branchName != "") {
        echo "checkout custom branch: ${branchName}; repo: ${repoName}"
        echo "Submodules processing: ${disableSubmodules}"
        echo "Include in polling: ${polling}; Include in changelog: ${changelog}"

        // NOTE: workspace clean could be implemented with [$class: 'WipeWorkspace']
        // OPTIMIZE: use shallow clone [$class: 'CloneOption', depth: 2, shallow: true]
        // IDEA: add *deleteUntrackedNestedRepositories: true* for Clean calls to avoid full WS wiping

        checkout changelog: changelog, poll: polling,
            scm: [$class: 'GitSCM', branches: [[name: "${branchName}"]], doGenerateSubmoduleConfigurations: false,
                    extensions:[
                        [$class: 'PruneStaleBranch'],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CleanCheckout'],
                        [$class: 'CheckoutOption', timeout: 30],
                        [$class: 'CloneOption', timeout: 60, noTags: false],
                        [$class: 'SubmoduleOption', disableSubmodules: disableSubmodules,
                            parentCredentials: true, recursiveSubmodules: true,
                            timeout: 60, reference: '', trackingSubmodules: false]
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: "${credId}", url: "${repoName}"]]
                ]
    } else {
        echo 'checkout from scm options'
        checkout scm
    }
}