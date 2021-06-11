import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeRender(osName, gpuName, attemptNum, Map options) {
	currentBuild.result = 'SUCCESS'

	String tool = options['tool'].split(':')[0].trim()
	String version = options['tool'].split(':')[1].trim()
	String scene_name = options['sceneName']
	String scene_user = options['sceneUser']
	String fail_reason = "Unknown"

	switch(osName) {
		case 'Windows':
			try {
				// Send attempt number
				render_service_send_render_attempt(attemptNum, options.id, options.django_url)
				// Clean up work folder
				cleanWS(osName)
				// Download render service scripts
				try {
					render_service_send_render_status("Downloading scripts and install requirements", options.id, options.django_url)
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
					render_service_send_render_status("Downloading scene", options.id, options.django_url)
					bat """
						if not exist "..\\..\\RenderServiceStorage" mkdir "..\\..\\RenderServiceStorage"
					"""
					render_service_clear_scenes(osName)
					if (options.fileSource == 'Storage') {
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
							
							bat """
								if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
								copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${options.sceneHash}"
							"""
						}
					} else if (options.fileSource == 'Repository') {
						def usernameOption = ""
						def passwordOption = ""
						
						if (options.repoLogin) {
							usernameOption  = "--username ${options.repoLogin}"
						}
						
						if (options.repoPassword) {
							withCredentials([string(credentialsId: "renderServiceKey", variable: "RS_PRIVKEY")]) {
								withEnv(["RS_PRIVKEY=${RS_PRIVKEY}"]) {
									def decryptedPassword = python3(".\\render_service_scripts\\decrypt_password.py --crypto ${options.repoPassword}").split('\r\n')[2].trim()
									passwordOption  = "--password ${decryptedPassword}"
								}
							}
						}
						
						print("Checkout SVN repository and copy it to the workspace")
						def ret = bat(
							script: """
									@svn co ${usernameOption} ${passwordOption} ${options.repoUrl} ..\\..\\RenderServiceStorage\\${scene_user}\\${options.repoUuid}"
									robocopy "..\\..\\RenderServiceStorage\\${scene_user}\\${options.repoUuid}" "repo" /s /e
									""",
							returnStatus: true
						)
						print("Status: ${ret}")
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
							// copy necessary scripts for render
							bat """
								copy "render_service_scripts\\blender_render.py" "."
								copy "render_service_scripts\\launch_blender.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_blender.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --min_samples ${options.minSamples} --max_samples ${options.maxSamples} --noise_threshold ${options.noiseThreshold} --height ${options.height} --width ${options.width} --startFrame ${options.startFrame} --endFrame ${options.endFrame} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;

						case 'Max':
							// copy necessary scripts for render
							bat """
								copy "render_service_scripts\\max_render.ms" "."
								copy "render_service_scripts\\launch_max.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_max.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --min_samples ${options.minSamples} --max_samples ${options.maxSamples} --noise_threshold ${options.noiseThreshold} --width ${options.width} --height ${options.height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;

						case 'Maya':
							// copy necessary scripts for render	
							bat """
								copy "render_service_scripts\\maya_render.py" "."
								copy "render_service_scripts\\maya_batch_render.py" "."
								copy "render_service_scripts\\launch_maya.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_maya.py --tool ${version} --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --min_samples ${options.minSamples} --max_samples ${options.maxSamples} --noise_threshold ${options.noiseThreshold} --width ${options.width} --height ${options.height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} --batchRender ${options.batchRender} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;

						case 'Maya (Redshift)':
							// copy necessary scripts for render	
							bat """
								copy "render_service_scripts\\redshift_render.py" "."
								copy "render_service_scripts\\launch_maya_redshift.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_maya_redshift.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --scene_name \"${scene_name}\" --min_samples ${options.minSamples} --max_samples ${options.maxSamples} --noise_threshold ${options.noiseThreshold} --width ${options.width} --height ${options.height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;
					
						case 'Maya (Arnold)':
							// copy necessary scripts for render	
							bat """
								copy "render_service_scripts\\arnold_render.py" "."
								copy "render_service_scripts\\launch_maya_arnold.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_maya_arnold.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --scene_name \"${scene_name}\" --min_samples ${options.minSamples} --max_samples ${options.maxSamples} --noise_threshold ${options.noiseThreshold} --width ${options.width} --height ${options.height} --startFrame ${options.startFrame} --endFrame ${options.endFrame} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;

						case 'Core':
							// copy necessary scripts for render	
							bat """
								copy ".\\render_service_scripts\\find_scene_core.py" "."
								copy ".\\render_service_scripts\\launch_core_render.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_core_render.py --django_ip \"${options.django_url}/\" --id ${id} --pass_limit ${options.iterations} --width ${options.width} --height ${options.height} --sceneName \"${scene_name}\" --startFrame ${options.startFrame} --endFrame ${options.endFrame} --gpu \"${options.GPU}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ")
							}
							break;

					}
				} catch(FlowInterruptedException e) {
					throw e
				} catch(e) {
					// if status == failure then copy full path and send to slack
					bat """
						mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
						copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
					"""
					// if exit code is greater than 0 or received any other exception -> script finished with unexpected exception
					String[] messageParts = e.getMessage().split(" ")
					Integer exitCode = messageParts[messageParts.length - 1].isInteger() ? messageParts[messageParts.length - 1].toInteger() : null
					if (exitCode == null || exitCode > 0) {
						fail_reason = "Unknown" 
					} else {
						fail_reason = "Expected exception"
					}
					throw e
				}
			} catch(FlowInterruptedException e) {
				throw e
			} catch(e) {
				println(e.toString())
				println(e.getMessage())
				println(e.getStackTrace())
				print e
				if (fail_reason != "Expected exception") {
					render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, fail_reason)
				}
				throw e
			}
	}
}

def main(String PCs, Map options) {

	timestamps {
		String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
		String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
		options['PRJ_PATH']="${PRJ_PATH}"
		options['JOB_PATH']="${JOB_PATH}"

		List tokens = PCs.tokenize(':')
		String osName = tokens.get(0)
		String deviceName = tokens.get(1)
		
		String renderDevice = ""
		if (deviceName == "ANY") {
			String tool = options['tool'].split(':')[0].replaceAll("\\(Redshift\\)", "").trim()
			renderDevice = tool
		} else {
			renderDevice = "gpu${deviceName}"
		}
		
		startRender(osName, deviceName, renderDevice, options)
	}    
	
}

def startRender(osName, deviceName, renderDevice, options) {
	stage("Send Build Number") {
		render_service_send_build_number(currentBuild.number, options.id, options.django_url)
	}

	def labels = "${osName} && RenderService && ${renderDevice}"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("Max attempts: ${options.maxAttempts}")
	def maxAttempts = "${options.maxAttempts}".toInteger()
	def testTasks = [:]
	def currentLabels = labels

	int attemptNum = 0
    
    def nodes = nodesByLabel label: labels, offline: false

	for (nodeName in nodes) {
		if (utils.isNodeIdle(nodeName)) {
			println("Checking the ${nodeName} storage for a scene")
			node(nodeName) {
				stage("Storage pre-check") {
					ws("WS/${options.PRJ_NAME}_Render") {
						try {
							Boolean sceneOrRepoExists
							switch (options.fileSource) {
								case 'Storage':
									sceneOrRepoExists = fileExists "..\\..\\RenderServiceStorage\\${options.sceneUser}\\${options.sceneHash}"
									break
								case 'Repository':
									sceneOrRepoExists = fileExists "..\\..\\RenderServiceStorage\\${options.sceneUser}\\${options.repoUuid}"
									break
							}
							if (sceneOrRepoExists) {
								print("[INFO] Scene exists on this PC. Trying to execute the job")
								stage("Render") {
									timeout(time: 65, unit: 'MINUTES') {
										attemptNum += 1
										executeRender(osName, deviceName, attemptNum, options)
										successfullyDone = true
									}
								}
							} else {
								print("[INFO] Scene wasn't found during pre-check")
							}
						} catch(FlowInterruptedException e) {
							throw e
						} catch (e) {
							rs_utils.handleException(this, e, nodeName)
							//Exclude failed node name
							currentLabels = currentLabels + " && !" + nodeName
							println(currentLabels)
						}
						if (successfullyDone || attemptNum == maxAttempts || attemptNum == nodesCount) {
							// Process finished - set attempt number as 0
							render_service_send_render_attempt(0, options.id, options.django_url)
						}
					}
				}
			}

			if (successfullyDone) {
				break
			}
		}
	}

	for (attemptNum += 1; attemptNum <= maxAttempts && attemptNum <= nodesCount && !successfullyDone; attemptNum++) {
		def currentNodeName = ""

		echo "Scheduling Render ${osName}:${deviceName}. Attempt #${attemptNum}"
		testTasks["Render-${osName}-${deviceName}"] = {
			node(currentLabels) {
				stage("Render") {
					timeout(time: 65, unit: 'MINUTES') {
						ws("WS/${options.PRJ_NAME}_Render") {
							currentNodeName = "${env.NODE_NAME}"
							try {
								executeRender(osName, deviceName, attemptNum, options)
								successfullyDone = true
							} catch(FlowInterruptedException e) {
								throw e
							} catch (e) {
								rs_utils.handleException(this, e, currentNodeName)
								//Exclude failed node name
								currentLabels = currentLabels + " && !" + currentNodeName
								println(currentLabels)
							}
							if (successfullyDone || attemptNum == maxAttempts || attemptNum == nodesCount) {
								// Process finished - set attempt number as 0
								render_service_send_render_attempt(0, options.id, options.django_url)
							}
						}
					}
				}
			}
		}

		parallel testTasks
	}

	if (!successfullyDone) {
		if (nodesCount == 0) {
			// master machine can't access necessary nodes. Run notification script on any machine
			stage("Notify about failure") {
				render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, 'No machine with specified configuration')
			}
		}
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
	
def call(String PCs = '',
	String id = '',
	String tool = '',
	String scene = '',  
	String pluginLink = '',
	String sceneName = '',
	String relPath = '',
	String sceneUser = '',
	String maxAttempts = '',
	String options = '',
	String timeout = '',
	String djangoUrl = '',
	String scriptsBranch = '',
	String sceneHash = '',
	String pluginHash = '',
	String fileSource = '',
	String repository = ''
	) {
	String PRJ_ROOT='RenderServiceRenderJob'
	String PRJ_NAME='RenderServiceRenderJob' 

	def optionsMap = parseOptions(options)
	def repositoryMap = parseOptions(repository)

	main(PCs,[
		enableNotifications:false,
		PRJ_NAME:PRJ_NAME,
		PRJ_ROOT:PRJ_ROOT,
		id:id,
		tool:tool,
		scene:scene,
		pluginLink:pluginLink,
		sceneName:sceneName,
		relPath: relPath,
		sceneUser:sceneUser,
		maxAttempts:maxAttempts,
		minSamples:optionsMap.min_samples,
		maxSamples:optionsMap.max_samples,
		noiseThreshold:optionsMap.noise_threshold,
		startFrame:optionsMap.start_frame,
		endFrame:optionsMap.end_frame,
		width:optionsMap.width,
		height:optionsMap.height,
		iterations:optionsMap.iterations,
		GPU:optionsMap.gpu,
		batchRender:optionsMap.batch_render,
		timeout:timeout,
		django_url:djangoUrl,
		scripts_branch:scriptsBranch,
		sceneHash:sceneHash,
		pluginHash:pluginHash,
		fileSource: fileSource,
		repoName: repositoryMap.name,
		repoUrl: repositoryMap.url,
		repoLogin: repositoryMap.login,
		repoPassword: repositoryMap.password,
		repoUuid: repositoryMap.uuid
		])
	}
