import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeBuildWindows(Map options) {
    String buildName = "${options.buildConfiguration}_${options.buildPlatform}"

    try {
        dir ("RadeonProRenderInventorPlugin") {
            // build plugin
            bat """
                set msbuild="${options.msBuildPath}"
                %msbuild% RadeonProRenderInventorPlugin.sln /target:build /maxcpucount /property:Configuration='${options.buildConfiguration}';Platform='{options.buildPlatform}' >> '..\\${STAGE_NAME}_${buildName}.log' 2>&1
            """

            // copy build results in separate directory
            bat """
                mkdir ..\\buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\{options.buildPlatform}\\${options.buildConfiguration}\\UsdConvertor.dll ..\\buildResults
            """

            // copy thirdparty libraries in results directory
            bat """
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\USD.NET.dll ..\\buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\UsdCs.dll ..\\buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\libusd_ms.dll ..\\buildResults
            """
        }
        
        zip archive: true, dir: "buildResults", glob: '', zipFile: "Windows_${buildName}.zip"

        bat """
            rename Windows_${win_build_name}.zip PluginWindows.zip
        """
        stash includes: "PluginWindows.zip", name: 'appWindows'
        options.pluginWinSha = sha1 "PluginWindows.zip"

    } catch (FlowInterruptedException error) {
        println "[INFO] Job was aborted during build stage"
        throw error
    } catch (e) {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        println "[ERROR] Failed to build Inventor plugin on Windows"
    }
}


def executeBuild(String osName, Map options) {
    try {
        cleanWS(osName)
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderInventorPlugin.git', true)
        
        switch(osName)
        {
            case 'Windows':
                executeBuildWindows(options);
                break;
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


def call(String projectBranch = "",
    String platforms = 'Windows',
    String buildConfiguration = "release",
    String buildPlatform = "x64",
    Boolean incrementVersion = true,
    Boolean forceBuild = false) {
    try {
        String PRJ_NAME="RadeonProRenderInventorPlugin"
        String PRJ_ROOT="rpr-plugins"

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

        String msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe"

        // TODO impelemnt PreBuild, Test and Deploy stages
        multiplatform_pipeline(platforms, null, this.&executeBuild, null, null,
                               [projectBranch:projectBranch,
                                incrementVersion:incrementVersion,
                                forceBuild:forceBuild,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                buildConfiguration:buildConfiguration,
                                buildPlatform:buildPlatform,
                                gpusCount:gpusCount,
                                BUILDER_TAG:"BuilderInv",
                                msBuildPath: msBuildPath,
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