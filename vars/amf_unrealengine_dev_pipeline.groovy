def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)
    
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
                echo 'sample image' > ./ReferenceImages/sample_image.txt
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
            run.bat >> ../${STAGE_NAME}.log  2>&1
            """
        }
        break;
    case 'OSX':
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {

        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')
        
        // update assets
        if(isUnix())
        {
            sh """
            ${CIS_TOOLS}/receiveFiles.sh ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/* ${CIS_TOOLS}/../TestResources/CoreAssets
            """
        }
        else
        {
            bat """
            %CIS_TOOLS%\\receiveFiles.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/* /mnt/c/TestResources/CoreAssets
            """
        }
        
        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"
        
        outputEnvironmentInfo(osName)
        
        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
            receiveFiles("${REF_PATH_PROFILE}/*", './Work/Baseline/')
            executeTestCommand(osName, options)    
        }
    }
    catch (e)
    {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        throw e
    }
    finally
    {}
}

def executeBuildWindows(Map options)
{
    dir('UnrealEngine_dev/integration')
    {
        bat """
        call Build.bat 4.21 amf shipping origin  >> ..\\..\\${STAGE_NAME}.log 2>&1
        call Build.bat 4.22 amf shipping origin  >> ..\\..\\${STAGE_NAME}.log 2>&1

        call Build.bat 4.21 stitch shipping origin  >> ..\\..\\${STAGE_NAME}.log 2>&1
        call Build.bat 4.22 stitch shipping origin  >> ..\\..\\${STAGE_NAME}.log 2>&1

        call Build.bat 4.21 amf development origin  >> ..\\..\\${STAGE_NAME}.log 2>&1
        call Build.bat 4.22 amf development origin  >> ..\\..\\${STAGE_NAME}.log 2>&1

        call Build.bat 4.21 stitch development origin  >> ..\\..\\${STAGE_NAME}.log 2>&1
        call Build.bat 4.22 stitch development origin  >> ..\\..\\${STAGE_NAME}.log 2>&1
        """
    }
}

def executeBuildOSX(Map options)
{
 
}

def executeBuildLinux(Map options)
{
    
}

def executeBuild(String osName, Map options)
{
    try {        
        dir('UnrealEngine_dev')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:amfdev/UnrealEngine_dev.git')
        }
        
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            executeBuildOSX(options);
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
        archiveArtifacts "UnrealEngine_dev/integration/Logs/**/*.*"
    }                        
}

def executePreBuild(Map options)
{
    currentBuild.description = ""
    ['projectBranch'].each
    {
        if(options[it] != 'master' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    cleanWS()
    try
    { 
       
    }
    catch (e)
    {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows',
         String PRJ_NAME="UnrealEngine_dev",
         String PRJ_ROOT="amf",
         Boolean updateRefs = false,
         Boolean enableNotifications = false) {
    try
    {

        multiplatform_pipeline(platforms, null, this.&executeBuild, null, null,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderU',
                                BUILD_TIMEOUT:240,
                                executeBuild:true,
                                executeTests:false])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
