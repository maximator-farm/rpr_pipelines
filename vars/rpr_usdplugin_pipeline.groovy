def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)

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
    if (!options['skipBuild'])
    {
        installPlugin(osName, options)
        //duct tape for migration to maya2019
        try {
            buildRenderCache(osName, "${options.stageName}.log")
        } catch(e) {
            println(e.toString())
            println("ERROR during building render cache")
        }
    }

    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            bat """
            run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
            """
        }
        break;
    case 'OSX':
        dir('scripts')
        {
            sh """
            ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log 2>&1
            """
        }
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
}

def executeBuildWindows(Map options)
{
    String CMD_BUILD_USD = """
    if exist USDgen rmdir /s/q USDgen
    if exist USDinst rmdir /s/q USDinst
    python USD\\build_scripts\\build_usd.py -v --build ${WORKSPACE}/USDgen/build --src ${WORKSPACE}/USDgen/src ${WORKSPACE}/USDinst > USD/${STAGE_NAME}_USD.log 2>&1
    """

    String CMAKE_KEYS_USD = """-DGLEW_LOCATION="${WORKSPACE}/USDinst" ^
    -DCMAKE_INSTALL_PREFIX="${WORKSPACE}/USDinst" ^
    -DUSD_INCLUDE_DIR="${WORKSPACE}/USDinst/include" -DUSD_LIBRARY_DIR="${WORKSPACE}/USDinst/lib" """

    CMD_BUILD_USD = options.rebuildUSD ? CMD_BUILD_USD : "echo \"Skip USD build\""
    CMAKE_KEYS_USD = options.enableHoudini ? "" : CMAKE_KEYS_USD

    withEnv(["PATH=c:\\python27\\;c:\\python27\\scripts\\;${PATH}", "WORKSPACE=${env.WORKSPACE.toString().replace('\\', '/')}"]) {
        bat """
        call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" amd64 >> ${STAGE_NAME}.log 2>&1

        ${CMD_BUILD_USD}

        set PATH=${WORKSPACE}\\USDinst\\bin;${WORKSPACE}\\USDinst\\lib;%PATH%
        set PYTHONPATH=${WORKSPACE}\\USDinst\\lib\\python;%PYTHONPATH%

        pushd RadeonProRenderThirdPartyComponents\\RadeonProRender-SDK\\Win
        mkdir rprTools
        move *.cpp rprTools
        move *.h rprTools
        popd

        mkdir RadeonProRenderUSD\\build
        pushd RadeonProRenderUSD\\build

        cmake -G "Visual Studio 15 2017 Win64" ${CMAKE_KEYS_USD} ^
        -DRPR_LOCATION="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Win" ^
        -DRPR_LOCATION_LIB="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Win/lib" ^
        -DRPR_LOCATION_INCLUDE="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Win/inc" ^
        -DRPR_BIN_LOCATION="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Win/bin" ^
        -DRIF_LOCATION="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing" ^
        -DRIF_LOCATION_LIB="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Windows/lib" ^
        -DRIF_LOCATION_INCLUDE="${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Windows/inc" ^
        -DRPR_BUILD_AS_HOUDINI_PLUGIN=${options.enableHoudini.toString().toUpperCase()} ^
        -DHOUDINI_ROOT="C:/Program Files/Side Effects Software/Houdini 18.0.260" ^
         .. >> ..\\..\\${STAGE_NAME}.log 2>&1

         python ../pxr/imaging/plugin/hdRpr/package/generatePackage.py -b . >> ../../${STAGE_NAME}.log 2>&1
         """
        // msbuild /t:Build /p:Configuration=Release hdRpr.sln >> ..\\..\\${STAGE_NAME}.log 2>&1

    }
}

def executeBuildOSX(Map options) {
    String CMD_BUILD_USD = """
    if [ -d "./USDgen" ]; then
        rm -fdr ./USDgen
    fi

    if [ -d "./USDinst" ]; then
        rm -fdr ./USDinst
    fi

    mkdir -p USDgen
    mkdir -p USDinst

    python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
    """

    String CMAKE_KEYS_USD = """-DGLEW_LOCATION=${WORKSPACE}/USDinst \
    -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/USDinst \
    -DUSD_INCLUDE_DIR=${WORKSPACE}/USDinst/include -DUSD_LIBRARY_DIR=${WORKSPACE}/USDinst/lib"""

    CMD_BUILD_USD = options.rebuildUSD ? CMD_BUILD_USD : "echo \"Skip USD build\""
    CMAKE_KEYS_USD = options.enableHoudini ? "" : CMAKE_KEYS_USD

    withEnv(["OS="]) {
        sh """
        ${CMD_BUILD_USD}

        export PATH=${WORKSPACE}/USDinst/bin:\$PATH
        export PYTHONPATH=${WORKSPACE}/USDinst/lib/python:\$PYTHONPATH

        pushd RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Mac
        mkdir rprTools
        mv *.cpp rprTools
        mv *.h rprTools
        popd

        mkdir -p RadeonProRenderUSD/build
        pushd RadeonProRenderUSD/build

        cmake ${CMAKE_KEYS_USD} -DRPR_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Mac \
        -DRPR_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Mac/lib \
        -DRPR_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Mac/inc \
        -DRIF_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing \
        -DRIF_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Mac/lib \
        -DRIF_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Mac/inc \
        -DRPR_BUILD_AS_HOUDINI_PLUGIN=${options.enableHoudini.toString().toUpperCase()} \
        -DHOUDINI_ROOT=/Applications/Houdini/Current/Frameworks/Houdini.framework/Versions/Current/Resources \
        -DCMAKE_BUILD_TYPE=Release \
        .. >> ../../${STAGE_NAME}.log 2>&1

        python ../pxr/imaging/plugin/hdRpr/package/generatePackage.py -b . >> ../../${STAGE_NAME}.log 2>&1
        """
        // make >> ../../${STAGE_NAME}.log 2>&1
    }
}

def executeBuildLinux(Map options) {
    String CMD_BUILD_USD = """
    if [ -d "./USDgen" ]; then
        rm -fdr ./USDgen
    fi

    if [ -d "./USDinst" ]; then
        rm -fdr ./USDinst
    fi

    mkdir -p USDgen
    mkdir -p USDinst

    python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
    """

    String CMAKE_KEYS_USD = """-DGLEW_LOCATION=${WORKSPACE}/USDinst \
    -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/USDinst \
    -DUSD_INCLUDE_DIR=${WORKSPACE}/USDinst/include -DUSD_LIBRARY_DIR=${WORKSPACE}/USDinst/lib"""

    CMD_BUILD_USD = options.rebuildUSD ? CMD_BUILD_USD : "echo \"Skip USD build\""
    CMAKE_KEYS_USD = options.enableHoudini ? "" : CMAKE_KEYS_USD
    // set $OS=null because it use in TBB build script
    withEnv(["OS="]) {
        // TODO: remove ln -s after Thirdparty update
        sh """
        ${CMD_BUILD_USD}

        export PATH=${WORKSPACE}/USDinst/bin:\$PATH
        export PYTHONPATH=${WORKSPACE}/USDinst/lib/python:\$PYTHONPATH

        ln -s ${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Linux/Ubuntu/lib64/libMIOpen.so.2 ${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Linux/Ubuntu/lib64/libMIOpen.so

        cd RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-Ubuntu
        mkdir rprTools
        mv *.cpp rprTools
        mv *.h rprTools
        cd ${WORKSPACE}

        mkdir -p RadeonProRenderUSD/build
        cd RadeonProRenderUSD/build

        cmake ${CMAKE_KEYS_USD} -DRPR_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-Ubuntu \
        -DRPR_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-Ubuntu/lib \
        -DRPR_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-Ubuntu/inc \
        -DRIF_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing \
        -DRIF_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Linux/Ubuntu/lib64 \
        -DRIF_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/Linux/Ubuntu/include \
        -DRPR_BUILD_AS_HOUDINI_PLUGIN=${options.enableHoudini.toString().toUpperCase()} \
        -DHOUDINI_ROOT=/opt/hfs18.0.260 \
        -DCMAKE_BUILD_TYPE=Release .. >> ../../${STAGE_NAME}.log 2>&1

        python ../pxr/imaging/plugin/hdRpr/package/generatePackage.py -b . >> ../../${STAGE_NAME}.log 2>&1
        """
        // make >> ../../${STAGE_NAME}.log 2>&1

    }
}

def executeBuildCentOS(Map options) {
    String CMD_BUILD_USD = """
    if [ -d "./USDgen" ]; then
        rm -fdr ./USDgen
    fi

    if [ -d "./USDinst" ]; then
        rm -fdr ./USDinst
    fi

    mkdir -p USDgen
    mkdir -p USDinst

    python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
    """

    String CMAKE_KEYS_USD = """-DGLEW_LOCATION=${WORKSPACE}/USDinst \
    -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/USDinst \
    -DUSD_INCLUDE_DIR=${WORKSPACE}/USDinst/include -DUSD_LIBRARY_DIR=${WORKSPACE}/USDinst/lib"""

    CMD_BUILD_USD = options.rebuildUSD ? CMD_BUILD_USD : "echo \"Skip USD build\""
    CMAKE_KEYS_USD = options.enableHoudini ? "" : CMAKE_KEYS_USD
    // set $OS=null because it use in TBB build script
    withEnv(["OS="]) {
        // TODO: remove ln -s after Thirdparty update
        sh """
        ${CMD_BUILD_USD}

        export PATH=${WORKSPACE}/USDinst/bin:\$PATH
        export PYTHONPATH=${WORKSPACE}/USDinst/lib/python:\$PYTHONPATH

        ln -s ${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/CentOS7/lib/libMIOpen.so.2 ${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/CentOS7/lib/libMIOpen.so

        cd RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-CentOS
        mkdir rprTools
        mv *.cpp rprTools
        mv *.h rprTools
        cd ${WORKSPACE}

        mkdir -p RadeonProRenderUSD/build
        cd RadeonProRenderUSD/build

        cmake ${CMAKE_KEYS_USD} -DRPR_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-CentOS \
        -DRPR_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-CentOS/lib \
        -DRPR_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProRender-SDK/Linux-CentOS/inc \
        -DRIF_LOCATION=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing \
        -DRIF_LOCATION_LIB=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/CentOS7/lib \
        -DRIF_LOCATION_INCLUDE=${WORKSPACE}/RadeonProRenderThirdPartyComponents/RadeonProImageProcessing/CentOS7/inc \
        -DRPR_BUILD_AS_HOUDINI_PLUGIN=${options.enableHoudini.toString().toUpperCase()} \
        -DHOUDINI_ROOT=/opt/hfs18.0.260 \
        -DCMAKE_BUILD_TYPE=Release .. >> ../../${STAGE_NAME}.log 2>&1

        python ../pxr/imaging/plugin/hdRpr/package/generatePackage.py -b . >> ../../${STAGE_NAME}.log 2>&1
        """
        // make >> ../../${STAGE_NAME}.log 2>&1

    }
}

def executeBuild(String osName, Map options) {
    try {
        dir('RadeonProRenderUSD') {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderUSD.git')
        }

        dir('RadeonProRenderThirdPartyComponents') {
            checkOutBranchOrScm(options['thirdpartyBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderThirdPartyComponents.git')
        }
        if (options.rebuildUSD) {
            dir('USD') {
                checkOutBranchOrScm(options['usdBranch'], 'git@github.com:PixarAnimationStudios/USD.git')
            }
        }

        outputEnvironmentInfo(osName)

        switch(osName) {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                executeBuildOSX(options);
                break;
            case 'CentOS':
                executeBuildCentOS(options);
                break;
            case 'CentOS7_6':
                executeBuildCentOS(options);
                break;
            default:
                executeBuildLinux(options);
        }
        archiveArtifacts "RadeonProRenderUSD/build/hdRpr-*.tar.gz"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        if (options.sendToRBS) {
            try {
                options.rbs_prod.setFailureStatus()
                options.rbs_dev.setFailureStatus()
            } catch (err) {
                println(err)
            }
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        if (options.rebuildUSD) {
            archiveArtifacts "USD/*.log"
        }
    }
}

def executePreBuild(Map options) {

    dir('RadeonProRenderUSD')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderUSD.git')

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

        if(options['incrementVersion'])
        {
            if("${BRANCH_NAME}" == "master" && "${AUTHOR_NAME}" != "radeonprorender")
            {
                echo "Incrementing version of change made by ${AUTHOR_NAME}."

                String majorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MAJOR_VERSION "', '')
                String minorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MINOR_VERSION "', '')
                String currentversion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')
                echo "currentversion ${majorVersion}.${minorVersion}.${currentversion}"

                new_version=version_inc(currentversion, 1, ' ')
                echo "new_version ${majorVersion}.${minorVersion}.${new_version}"

                version_write("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', new_version, '')

                String updatedversion=version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')
                echo "updatedversion ${majorVersion}.${minorVersion}.${updatedversion}"

                bat """
                git add cmake/defaults/Version.cmake
                git commit -m "buildmaster: version update to ${majorVersion}.${minorVersion}.${updatedversion}"
                git push origin HEAD:master
                """

                //get commit's sha which have to be build
                options['projectBranch'] = bat ( script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            }
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
}

def call(String projectBranch = "",
        String thirdpartyBranch = "master",
        String usdBranch = "master",
        String testsBranch = "master",
        String platforms = 'Windows;Ubuntu18;OSX;CentOS7_6',
        Boolean updateRefs = false,
        Boolean enableNotifications = true,
        Boolean incrementVersion = true,
        Boolean skipBuild = false,
        String renderDevice = "gpu",
        String testsPackage = "",
        String tests = "",
        Boolean forceBuild = false,
        Boolean splitTestsExectuion = false,
        Boolean sendToRBS = false,
        Boolean enableHoudini = true,
        Boolean rebuildUSD = false)
{
    try
    {
        String PRJ_NAME="RadeonProRenderUSD"
        String PRJ_ROOT="rpr-plugins"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, null,
                               [projectBranch:projectBranch,
                                thirdpartyBranch:thirdpartyBranch,
                                usdBranch:usdBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                skipBuild:skipBuild,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests,
                                executeBuild:true,
                                executeTests:false,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                splitTestsExectuion:splitTestsExectuion,
                                sendToRBS:sendToRBS,
                                TEST_TIMEOUT:30,
                                enableHoudini:enableHoudini,
                                rebuildUSD:rebuildUSD,
                                BUILDER_TAG:'Builder6'
                                ])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
