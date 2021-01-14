def call(String osName, String tool, Map options, String credentialsId = '')
{
    String customBuildLink = ""
    String extension = ""

    switch(osName)
    {
        case 'Windows':
            customBuildLink = options['customBuildLinkWindows']
            extension = "msi"
            break;
        case 'OSX':
            customBuildLink = options['customBuildLinkOSX']
            extension = "dmg"
            break;
        default:
            customBuildLink = options['customBuildLinkLinux']
            extension = "run"
    }

    if (tool == "Blender") {
        extension = "zip"
    }

    print "[INFO] Used specified pre built plugin for ${tool}."

    if (customBuildLink.startsWith("https://builds.rpr"))
    {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'builsRPRCredentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "OSX") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkOSX']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                """
            }
        }
    } 
    else if (customBuildLink.startsWith("https://rpr.cis")) 
    {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsUser', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "OSX") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkOSX']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                """
            }
        }
    }
    else
    {
        if (credentialsId) {
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                if (osName == "Windows") {
                    bat """
                        curl -L -o RadeonProRender${tool}_${osName}.${extension} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                    """
                } else if (osName == "OSX") {
                    sh """
                        curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkOSX']}"
                    """
                } else {
                    sh """
                        curl -L -o RadeonProRender${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                    """
                }
            }
        } else {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "OSX") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} "${options['customBuildLinkOSX']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extension} "${options['customBuildLinkLinux']}"
                """
            }
        }
    }

    // We haven't any branch so we use sha1 for identifying plugin build
    def pluginSha = sha1 "RadeonProRender${tool}_${osName}.${extension}"
    println "Downloaded plugin sha1: ${pluginSha}"

    switch(osName)
    {
        case 'Windows':
            options['pluginWinSha'] = pluginSha
            break;
        case 'OSX':
            options['pluginOSXSha'] = pluginSha
            break;
        default:
            options['pluginUbuntuSha'] = pluginSha
    }
}
