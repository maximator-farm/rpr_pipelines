import groovy.transform.Field
import groovy.util.FileTreeBuilder
import groovy.json.JsonOutput;

// clone repository and download tests executable
def executePrebuild(String repo)
{
    try {
        def tree = new FileTreeBuilder()
        tree.dir('OpenAMF')
        {
            def sout = new StringBuilder(), serr = new StringBuilder()
            def proc = ["git", "clone", repo].execute()
            proc.consumeProcessOutput(sout, serr)
            proc.waitForOrKill(1000)
            println "out> $sout err> $serr"
        }
    } catch (e){
        println "[ERROR] Couldn't checkout-${repo}"
        println e.getMessage()
    }
    
}

// build OpenAMF
def executeBuild()
{

}

// execute tests
def executeTests(String osName, String asicName, Map options)
{
    try {
        cleanWS(osName)
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:amfdev/AMF.git')
        println "[INFO] Preparing on ${env.NODE_NAME} successfully finished."
    } catch(e) {
        println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
        println(e.toString())
        throw e
    }
}

// call pipeline
def call(String projectRepo = "git@github.com:amfdev/AMF.git")
{

}

executePrebuild(
    "https://github.com/amfdev/AMF.git"
)
