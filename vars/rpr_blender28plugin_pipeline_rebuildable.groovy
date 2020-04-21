def executePreparation(Map options) {
    String PRJ_NAME="RadeonProRenderBlender2.8Plugin_Rebuildable"
    String PRJ_ROOT="rpr-plugins"

    gpusCount = 0
    options.platforms.split(';').each()
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

    //TODO add all Preparation logic
}

def executePreBuild(Map options)
{
    //TODO add preBuild logic
}

def executeBuild(String osName, Map options) {
    //TODO add Build logic
}


def call(String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "") {

    try {
        multiplatform_pipeline(platforms, this.&executePreparation, this.&executePreBuild, this.&executeBuild,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                testsPackage:testsPackage,
                                tests:tests,
                                reportName:'Test_20Report',
                                TEST_TIMEOUT:90,
                                TESTER_TAG:"Blender2.8",
                                BUILDER_TAG:"BuildBlender2.8",
                                WAIT_TIEMOUT:1,
                                testsJobName:"RadeonProRenderBlender2.8Tests"
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