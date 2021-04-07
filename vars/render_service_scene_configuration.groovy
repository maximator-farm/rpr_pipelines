import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeConfiguration(osName, attemptNum, Map options) {
    currentBuild.result = 'SUCCESS'

    String tool = options['tool'].split(':')[0].trim()
    String version = options['tool'].split(':')[1].trim()
    String scene_name = options['sceneName']
    String scene_user = options['sceneUser']
    String fail_reason = "Unknown"

    switch(osName) {
        case 'Windows':
            try {
                // Clean up work folder
                cleanWS(osName)
                // Download render service scripts
                try {
                    checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
                    dir(".\\install"){
                        bat '''
                        install_pylibs.bat
                        '''
                    }
                } catch(FlowInterruptedException e) {
                    throw e
                } catch(e) {
                    fail_reason = "Downloading scripts failed"
                    throw e
                }

                // download and install plugin
                if (options["pluginLink"]) {
                    render_service_install_plugin(options["pluginLink"], options["pluginHash"], tool, version, options.id, options.django_url)
                }

                // download scene, check if it is already downloaded
                try {
                    // initialize directory RenderServiceStorage
                    bat """
                        if not exist "..\\..\\RenderServiceStorage" mkdir "..\\..\\RenderServiceStorage"
                    """
                    render_service_clear_scenes(osName)
                    Boolean sceneExists = fileExists "..\\..\\RenderServiceStorage\\${scene_user}\\${options.sceneHash}"
                    if (sceneExists) {
                        print("[INFO] Scene is copying from Render Service Storage on this PC")
                        bat """
                            copy "..\\..\\RenderServiceStorage\\${scene_user}\\${options.sceneHash}" "${scene_name}"
                        """
                    } else {
                        print("[INFO] Scene wasn't found in Render Service Storage on this PC. Downloading it.")
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
                            bat """
                                curl -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.scene}"
                            """
                        }
                        if (options['action'] == 'Read') {
                            bat """
                                if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
                                copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${options.sceneHash}"
                            """
                        }
                    }
                } catch(FlowInterruptedException e) {
                    throw e
                } catch(e) {
                    fail_reason = "Downloading scene failed"
                    throw e
                }

                try {
                    switch(tool) {
                        case 'Blender':
                            if (options["id_render"] && options["django_render_url"]) {
                                try {
                                    bat """
                                        copy "render_service_scripts\\blender_render.py" "."
                                        copy "render_service_scripts\\launch_blender.py" "."
                                    """
                                    // Launch render
                                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
                                        python3("launch_blender.py --tool ${version} --django_ip \"${options.django_render_url}/\" --scene_name \"${scene_name}\" --id ${options.id_render} --min_samples 16 --max_samples 32 --noise_threshold 0.1 --height 150 --width 150 --startFrame 1 --endFrame 1 --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout 120 ")
                                    }
                                } catch(FlowInterruptedException e) {
                                    throw e
                                } catch(e) {
                                    fail_reason = "Failed sample render. Maybe incorrect scene or node problems. Check logs."
                                    throw e
                                }
                            }
                            try {
                                // copy necessary scripts for render and start read process
                                bat """
                                    copy "render_service_scripts\\scene_scanning\\read_blender_configuration.py" "."
                                    copy "render_service_scripts\\scene_scanning\\write_blender_configuration.py" "."
                                    copy "render_service_scripts\\scene_scanning\\launch_blender_scan.py" "."
                                """
                                // Launch render
                                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
                                    python3("launch_blender_scan.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --action \"${options.action}\" --configuration_options \"${options.configurationOptions}\" --options_structure \"${options.optionsStructure}\" ")
                                }
                                if (options['action'] == 'Write') {
                                    String updatedSceneHash = sha1 scene_name
                                    bat """
                                        if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
                                        copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${updatedSceneHash}"
                                    """
                                }
                                break;
                            } catch(FlowInterruptedException e) {
                                throw e
                            } catch(e) {
                                fail_reason = "Failed configuration" 
                                throw e
                            }
                    }

                } catch(FlowInterruptedException e) {
                    throw e
                } catch(e) {
                    bat """
                        mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
                        copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
                    """
                    throw e
                }
            } catch(FlowInterruptedException e) {
                throw e
            } catch(e) {
                println(e.toString())
                println(e.getMessage())
                println(e.getStackTrace())
                print e
                println(fail_reason)
                throw e
            }
    }
}

def main(Map options) {

    timestamps {
        String PRJ_PATH = "${options.PRJ_ROOT}/${options.PRJ_NAME}"
        String JOB_PATH = "${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
        options['PRJ_PATH'] = "${PRJ_PATH}"
        options['JOB_PATH'] = "${JOB_PATH}"

        String osName = 'Windows'
        
        startConfiguration(osName, options)
    }    
    
}

def startConfiguration(osName, options) {
    stage("Send Build Number") {
        render_service_send_build_number(currentBuild.number, options.id, options.django_url)
        if (options["id_render"] && options["django_render_url"]) {
            render_service_send_build_number(currentBuild.number, options.id_render.toString(), options.django_render_url)
        }
    }
    
    def labels = "${osName} && RenderService"
    def nodesCount = getNodesCount(labels)
    boolean successfullyDone = false

    print("Max attempts: ${options.maxAttempts}")
    def maxAttempts = "${options.maxAttempts}".toInteger()
    def currentLabels = labels
    for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
        def currentNodeName = ""

        echo "Scheduling Configuration ${osName}. Attempt #${attemptNum}"
        node(currentLabels) {
            stage("Configuration") {
                timeout(time: 15, unit: 'MINUTES') {
                    ws("WS/${options.PRJ_NAME}_Configuration") {
                        currentNodeName = "${env.NODE_NAME}"
                        try {
                            executeConfiguration(osName, attemptNum, options)
                            successfullyDone = true
                        } catch(FlowInterruptedException e) {
                            throw e
                        } catch (e) {
                            print e
                            //Exclude failed node name
                            currentLabels = currentLabels + " && !" + currentNodeName
                            println("[INFO] Updated labels: ${currentLabels}")
                        }
                    }
                }
            }
        }
        
        if (successfullyDone) {
            break
        }
    }

    if (!successfullyDone) {
        render_service_send_render_status('Failure', options.id, options.django_url)
        throw new Exception("Job was failed by all used nodes!")
    } else {
        currentBuild.result = 'SUCCESS'
    }
}

def getNodesCount(labels) {
    def nodes = nodesByLabel label: labels, offline: false
    def nodesCount = nodes.size()

    return nodesCount
}

@NonCPS
def parseOptions(String options) {
	def jsonSlurper = new groovy.json.JsonSlurperClassic()

	return jsonSlurper.parseText(options)
}

def call(String id = '',
    String tool = '',
    String scene = '',
    String pluginLink = '',
    String sceneName = '',
    String sceneUser = '',
    String maxAttempts = '',
    String action = '',
    String djangoUrl = '',
    String scriptsBranch = '',
    String sceneHash = '',
    String pluginHash = '',
    String configurationOptions = '',
    String optionsStructure = '',
    String sampleJobParams = ''
    ) {
    String PRJ_ROOT = 'RenderServiceSceneConfiguration'
    String PRJ_NAME = 'RenderServiceSceneConfiguration' 

    configurationOptions = configurationOptions.replace('\"', '\"\"')
    optionsStructure = optionsStructure.replace('\"', '\"\"')
    def sampleJobParamsMap = parseOptions(sampleJobParams)

    main([
        id: id,
        PRJ_NAME: PRJ_NAME,
        PRJ_ROOT: PRJ_ROOT,
        tool: tool,
        scene: scene,
        pluginLink: pluginLink,
        sceneName: sceneName,
        sceneUser: sceneUser,
        maxAttempts: maxAttempts,
        action: action,
        django_url: djangoUrl,
        scripts_branch: scriptsBranch,
        sceneHash: sceneHash,
        pluginHash: pluginHash,
        configurationOptions: configurationOptions,
        optionsStructure: optionsStructure,
        id_render: sampleJobParamsMap.id,
        django_render_url: sampleJobParamsMap.django_url
        ])
    }
