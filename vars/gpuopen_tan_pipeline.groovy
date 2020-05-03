
def executeTests(String osName, String asicName, Map options) {
    
}

def executeBuildWindows(Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5-dll64/*", './tan/tanlibrary/src/fftw-3.3.5-dll64')

    bat """
        mkdir thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64
        xcopy C:\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64 thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64 /s/y/i
    """

    options.winVisualStudioVersion.each() { vs_ver ->
        options.winBuildConfiguration.each() { build_conf ->
            options.winTool.each() { tool ->

                println "Current VS version: ${vs_ver}."
                println "Current build configuration: ${build_conf}."
                println "Current tool: ${tool}."

                switch(vs_ver) {
                    case '2015':
                        options.visualStudio = "Visual Studio 14 2015"
                        options.msBuildPath = "C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe"
                        break;
                    case '2017':
                        options.visualStudio = "Visual Studio 15 2017"
                        options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe"
                        break;
                    case '2019':
                        options.visualStudio = "Visual Studio 16 2019"
                        options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe"
                }

                dir('tan\\build\\cmake') {

                    if(fileExists("vs${vs_ver}")){
                        bat """
                            rd /s /q "vs${vs_ver}"
                            mkdir "vs${vs_ver}"
                        """
                    }
                    
                    options.openCL_dir = "..\\..\\..\\..\\thirdparty\\OpenCL-Headers"
                    options.portaudio_dir = "..\\..\\..\\..\\..\\thirdparty\\portaudio"

                    try {
                        dir ("vs${vs_ver}") {
                            bat """
                                SET CMAKE_PREFIX_PATH=..\\..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\lib\\cmake\\Qt5Widgets
                                cmake .. -G "${options.visualStudio}" -A x64 -DOpenCL_INCLUDE_DIR="${options.openCL_dir}" -DPortAudio_DIR="${options.portaudio_dir}" -DDEFINE_AMD_OPENCL_EXTENSION=1 >> ..\\..\\..\\..\\${STAGE_NAME}.vs${vs_ver}.${build_conf}.${tool}.log 2>&1
                            """
                        }
  
                        if (tool == "msbuild") {
                            dir ("vs${vs_ver}") {
                                bat """
                                    set msbuild="${options.msBuildPath}"
                                    %msbuild% TAN.sln /target:build /maxcpucount /property:Configuration=${build_conf};Platform=x64 >> ..\\..\\..\\..\\${STAGE_NAME}.vs${vs_ver}.${build_conf}.${tool}.log 2>&1
                                """
                            }
                        } else if (tool == "cmake") {
                            bat """
                                cmake --build vs${vs_ver} --config ${build_conf} >> ..\\..\\..\\${STAGE_NAME}.vs${vs_ver}.${build_conf}.${tool}.log 2>&1
                            """
                        }
                        
                        bat """
                            mkdir bin
                            copy vs${vs_ver}\\cmake-TAN-bin\\${build_conf}\\TrueAudioNext.dll bin
                            copy vs${vs_ver}\\cmake-TrueAudioVR-bin\\${build_conf}\\TrueAudioVR.dll bin
                            copy vs${vs_ver}\\cmake-GPUUtilities-bin\\${build_conf}\\GPUUtilities.dll bin
                            copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Core*.dll bin
                            copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Widgets*.dll bin
                            copy ..\\..\\..\\thirdparty\\Qt\\Qt5.9.9\\5.9.9\\msvc2017_64\\bin\\Qt5Gui*.dll bin
                            copy ..\\..\\..\\thirdparty\\portaudio\\build\\msvc\\x64\\Debug\\portaudio_x64.dll bin
                            copy vs${vs_ver}\\cmake-TALibDopplerTest-bin\\${build_conf}\\TALibDopplerTest.exe bin
                            copy vs${vs_ver}\\cmake-TALibTestConvolution-bin\\${build_conf}\\TALibTestConvolution.exe bin
                            copy vs${vs_ver}\\cmake-RoomAcousticQT-bin\\${build_conf}\\RoomAcousticsQT.exe bin
                        """
                        zip archive: true, dir: 'bin', glob: '', zipFile: "Windows_vs${vs_ver}_${build_conf}_${tool}.zip"
                    } catch (e) {
                        currentBuild.result = "FAILED"
                        println "[ERROR] Failed to build TAN on Windows"
                    } 
                }
            }
        }
    }   
}

def executeBuildOSX(Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5/*", './tan/tanlibrary/src/fftw-3.3.5')

    options.osxTool.each() { tool ->

        println "Current tool: ${tool}."

        dir('tan\\build\\cmake') {
            sh """
                rm -rf ./macos
                mkdir macos
            """
            dir("macos") {
                try {
                    options.cmake = "/usr/local/Cellar/qt/5.13.1"
                    options.opencl_headers = "../../../../thirdparty/OpenCL-Headers"
                    options.portaudio = "../../../../../thirdparty/portaudio"

                    if (tool == "cmake") {
                        sh """
                            cmake .. -DCMAKE_PREFIX_PATH="${options.cmake}" -DOpenCL_INCLUDE_DIR="${options.opencl_headers}" -DPortAudio_DIR="${options.portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1 -DRTQ_ENABLED=1 >> ../../../../${STAGE_NAME}.${tool}.log 2>&1
                        """
                    } else if (tool == "xcode") {
                        sh """
                            cmake -G "Xcode" .. -DCMAKE_PREFIX_PATH="${options.cmake}" -DOpenCL_INCLUDE_DIR="${options.opencl_headers}" -DPortAudio_DIR="${options.portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1 -DRTQ_ENABLED=1 >> ../../../../${STAGE_NAME}.${tool}.log 2>&1
                        """
                    }
                    
                    sh """
                        make VERBOSE=1 >> ../../../../${STAGE_NAME}.${tool}.log 2>&1
                    """

                    sh """
                        mkdir bin
                        cp cmake-TAN-bin/libTrueAudioNext.dylib bin
                        cp cmake-TrueAudioVR-bin/libTrueAudioVR.dylib bin
                        cp cmake-GPUUtilities-bin/libGPUUtilities.dylib bin
                        cp cmake-TALibDopplerTest-bin/TALibDopplerTest bin
                        cp cmake-TALibTestConvolution-bin/TALibTestConvolution bin
                        cp cmake-RoomAcousticQT-bin/RoomAcousticsQT bin
                    """
                    zip archive: true, dir: 'bin', glob: '', zipFile: "OSX_${tool}_Release.zip"
                } catch (e) {
                    currentBuild.result = "FAILED"
                    println "[ERROR] Failed to build TAN on OSX"
                } 
            }
        }
    }
}

def executeBuildLinux(String osName, Map options) {

    receiveFiles("gpuopen/OpenCL-Headers/*", './thirdparty/OpenCL-Headers')
    receiveFiles("gpuopen/portaudio/*", './thirdparty/portaudio')
    receiveFiles("gpuopen/fftw-3.3.5/*", './tan/tanlibrary/src/fftw-3.3.5')

    dir('tan\\build\\cmake') {
        sh """
            rm -rf ./linux
            mkdir linux
        """
        dir ("linux") {
            try {
                options.cmake = "/usr/bin/gcc"
                options.opencl_headers = "../../../../thirdparty/OpenCL-Headers"
                options.opencl_lib = "/usr/lib/x86_64-linux-gnu/libOpenCL.so"
                options.portaudio = "../../../../../thirdparty/portaudio"

                sh """
                    cmake .. -DCMAKE_PREFIX_PATH="${options.cmake}" -DOpenCL_INCLUDE_DIR="${options.opencl_headers}" -DOpenCL_LIBRARY="${options.opencl_lib}" -DPortAudio_DIR="${options.portaudio}" -DDEFINE_AMD_OPENCL_EXTENSION=1 >> ../../../../${STAGE_NAME}.log 2>&1
                """

                sh """
                    make VERBOSE=1 >> ../../../../${STAGE_NAME}.log 2>&1
                """

                sh """
                    mkdir bin
                    cp linux/cmake-TAN-bin/libTrueAudioNext.so bin
                    cp linux/cmake-TrueAudioVR-bin/libTrueAudioVR.so bin
                    cp linux/cmake-GPUUtilities-bin/libGPUUtilities.so bin
                    cp linux/cmake-TALibDopplerTest-bin/TALibDopplerTest bin
                    cp linux/cmake-TALibTestConvolution-bin/TALibTestConvolution bin
                    cp linux/cmake-RoomAcousticQT-bin/RoomAcousticsQT bin
                """
                zip archive: true, dir: 'bin', glob: '', zipFile: "Ubuntu18_Release.zip"
            } catch (e) {
                println(e.getMessage())
                currentBuild.result = "FAILED"
                println "[ERROR] Failed to build TAN on Ubuntu18"
            } 
        }
    }
}
def executeBuild(String osName, Map options) {
    try {

        cleanWS(osName)
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/TAN.git', true)
        
        switch(osName)
        {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                withEnv(["PATH=$WORKSPACE:$PATH"]) {
                    executeBuildOSX(options);
                }
                break;
            default:
                withEnv(["PATH=$PWD:$PATH"]) {
                    executeBuildLinux(osName, options);
                }
        }
    } catch (e) {
        options.failureMessage = "[ERROR] Failed to build plugin on ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options) {

    currentBuild.description = ""
    ['projectBranch'].each
    {
        if(options[it] != 'master' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }

    checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/TAN.git', true)

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

    if(options['incrementVersion'])
    {
        if("${BRANCH_NAME}" == "master" && "${AUTHOR_NAME}" != "radeonprorender")
        {
            options.testsPackage = "smoke"
            echo "[INFO] Incrementing version of change made by ${AUTHOR_NAME}."
            String currentversion=version_read("${env.WORKSPACE}\\version.h", '#define TRUE_AUDIO_NEXT_VERSION')
            echo "[INFO] Current version: ${currentversion}"

            new_version=version_inc(currentversion, 3)
            echo "[INFO] New version: ${new_version}"

            version_write("${env.WORKSPACE}\\version.h", '#define TRUE_AUDIO_NEXT_VERSION', new_version)

            String updatedversion=version_read("${env.WORKSPACE}\\version.h", '#define TRUE_AUDIO_NEXT_VERSION')
            echo "[INFO] Updated version: ${updatedversion}"

            bat """
                git add version.h
                git commit -m "buildmaster: version update to ${updatedversion}"
                git push origin HEAD:master
            """

            //get commit's sha which have to be build
            options['projectBranch'] = bat (script: "git log --format=%%H -1", returnStdout: true).split('\r\n')[2].trim()
            options['executeBuild'] = true
            options['executeTests'] = true
        } else {

            options.testsPackage = "smoke"
            if(commitMessage.contains("CIS:BUILD"))
            {
                options['executeBuild'] = true
            }

            if(commitMessage.contains("CIS:TESTS"))
            {
                options['executeBuild'] = true
                options['executeTests'] = true
            }

            if (env.CHANGE_URL)
            {
                echo "branch was detected as Pull Request"
                options['executeBuild'] = true
                options['executeTests'] = true
                options.testsPackage = "smoke"
            }

            if("${BRANCH_NAME}" == "master")
            {
               echo "rebuild master"
               options['executeBuild'] = true
               options['executeTests'] = true
               options.testsPackage = "smoke"
            }
        }
    }

    try {
        options.pluginVersion = version_read("${env.WORKSPACE}\\version.h", '#define TRUE_AUDIO_NEXT_VERSION')
    } catch (e) {
        println "[WARNING] Can't detect TAN version"
    }
    
    if (env.CHANGE_URL) {
        options.AUTHOR_NAME = env.CHANGE_AUTHOR_DISPLAY_NAME
        if (env.CHANGE_TARGET != 'master') {
            options['executeBuild'] = false
            options['executeTests'] = false
        }
        options.commitMessage = env.CHANGE_TITLE
    }

    // if manual job
    if(options['forceBuild'])
    {
        options['executeBuild'] = true
        options['executeTests'] = true
    }

    currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
    if (!env.CHANGE_URL) {
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
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }
}

def executeDeploy(Map options, List platformList, List testResultList) {
    //TODO add deploy logic
}


def call(String projectBranch = "",
    String testsBranch = "",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    String winBuildConfiguration = "Release",
    String winTool = "cmake,msbuild",
    String winVisualStudioVersion = "2017",
    String osxTool = "cmake",
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    Boolean forceBuild = false,
    Boolean skipBuild = false,
    String testsPackage = "",
    String tests = "") {
    try {
        String PRJ_NAME="TAN"
        String PRJ_ROOT="GPUOpen"

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

        winBuildConfiguration = winBuildConfiguration.split(',')
        winTool = winTool.split(',')
        winVisualStudioVersion = winVisualStudioVersion.split(',')
        osxTool = osxTool.split(',')

        println "Visual Studio version: ${winVisualStudioVersion}"
        println "Win configuration: ${winBuildConfiguration}"
        println "Win tool: ${winTool}"
        println "OSX tool: ${osxTool}"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                enableNotifications:enableNotifications,
                                incrementVersion:incrementVersion,
                                forceBuild:forceBuild,
                                skipBuild:skipBuild,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                winBuildConfiguration:winBuildConfiguration,
                                winTool:winTool,
                                winVisualStudioVersion:winVisualStudioVersion,
                                osxTool:osxTool,
                                testsPackage:testsPackage,
                                tests:tests,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:90,
                                DEPLOY_TIMEOUT:150,
                                BUILDER_TAG:"BuilderT",
                                ])
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
