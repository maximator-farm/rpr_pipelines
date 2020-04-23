def executePreparation(String platforms, Map options) {
    options.PRJ_NAME="RadeonProRenderBlender2.8Plugin_Rebuildable"
    options.PRJ_ROOT="rpr-plugins"

    gpusCount = 0
    platforms.split(';').each()
    { platform ->
        List tokens = platform.tokenize(':')
        if (tokens.size() > 1)
        {
            gpuNames = tokens.get(1)
            gpuNames.split(',').each()
            {
                gpusCount += 1
            }
        }
    }

    options.executeTests = !options.rebuildReport
    //TODO add all Preparation logic
}

def executePreBuild(Map options)
{
    //TODO add preBuild logic
}

def executeBuild(String osName, Map options) {
    //TODO add Build logic
}

def executeDeploy(Map options, List platformList, Map testsBuildsIds) {
    //TODO add Deploy logic
}


def call(String pipelinesBranch = "",
    String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "",
    String buildId = "",
    Boolean rebuildReport = false) {

    try {
        multiplatform_pipeline_rebuildable(platforms, this.&executePreparation, this.&executePreBuild, this.&executeBuild, this.&executeDeploy,
                               [pipelinesBranch:pipelinesBranch,
                                projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                testsPackage:testsPackage,
                                tests:tests,
                                reportName:'Test_20Report',
                                TEST_TIMEOUT:90,
                                DEPLOY_TIMEOUT:150,
                                TESTER_TAG:"Blender2.8",
                                BUILDER_TAG:"BuildBlender2.8",
                                testsJobName:"DevRadeonProRenderBlender2.8Tests",
                                buildId:buildId,
                                rebuildReport:rebuildReport
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