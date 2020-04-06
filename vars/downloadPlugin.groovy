def call(String osName, String tool, Map options)
{
    String customBuildLink = ""

    switch(osName)
    {
        case 'Windows':
            customBuildLink = options['customBuildLinkWindows']
            break;
        case 'OSX':
            customBuildLink = options['customBuildLinkOSX']
            break;
        default:
            customBuildLink = options['customBuildLinkLinux']
    }

    print "[INFO] Used specified pre built plugin for Blender."

    if (customBuildLink.startsWith("https://builds.rpr")) 
    {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'builsRPRCredentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.zip -u %USERNAME%:%PASSWORD% "${customBuildLink}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.zip -u %USERNAME%:%PASSWORD% '"${customBuildLink}"'
                """
            }
        }
    }
    else
    {
        if (osName == "Windows") {
            bat """
                curl -L -o RadeonProRender${tool}_${osName}.zip "${customBuildLink}"
            """
        } else {
            sh """
                curl -L -o RadeonProRender${tool}_${osName}.zip "${customBuildLink}"
            """
        }
    }

    // We haven't any branch so we use sha1 for idetifying plugin build
    def pluginSha = sha1 "RadeonProRender${tool}_${osName}.zip"
    switch(osName)
    {
        case 'Windows':
            options.pluginWinSha = pluginSha
            break;
        case 'OSX':
            options.pluginOSXSha = pluginSha
            break;
        default:
            options.pluginUbuntuSha = pluginSha
    }
}
