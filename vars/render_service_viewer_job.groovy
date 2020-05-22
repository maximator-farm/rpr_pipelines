def executeBuildViewer(osName, gpuName, attemptNum, isLastAttempt, Map options) {
    currentBuild.result = 'SUCCESS'
   
   	String scene_name = options['scene_name']
   	String fail_reason = "Unknown"
    echo "Options: ${options}"
    
    try {
		// Send attempt number
		render_service_send_render_attempt(attemptNum, options.id, options.django_url)
		// Clean up work folder
		cleanWS(osName)

		// Download render service scripts
		try {
			render_service_send_render_status("Downloading scripts and install requirements", options.id, options.django_url)
			checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
			dir("install"){
				bat '''
				install_pylibs.bat
				'''
			}

			bat """
				copy "render_service_scripts\\send_viewer_results.py" "."
			"""	
		} catch(e) {
			fail_reason = "Downloading scripts failed"
			throw e
		}

		dir("viewer_dir") {
			try {
				render_service_send_render_status("Downloading viewer", options.id, options.django_url)
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsCredentials', usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_PASSWORD']]) {
					bat """
						curl --retry 3 -L -O -J -u %JENKINS_USERNAME%:%JENKINS_PASSWORD% "https://rpr.cis.luxoft.com/job/RadeonProViewerAuto/job/master/${options.viewer_version}/artifact/RprViewer_Windows.zip"
					"""
				}
			} catch(e) {
				fail_reason = "Downloading viewer failed"
				throw e
			}

			try {
				render_service_send_render_status("Downloading scene", options.id, options.django_url)
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
					bat """
						curl --retry 3 -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.scene_link}"
					"""
				}
			} catch(e) {
				fail_reason = "Downloading scene failed"
				throw e
			}
		}

		try {
			bat """
				copy "render_service_scripts\\configure_viewer.py" "."
			"""		

			render_service_send_render_status("Building RPRViewer Package", options.id, options.django_url)
			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
				python3("configure_viewer.py --version ${options.viewer_version} --id ${id} --django_ip \"${options.django_url}/\" --build_number ${currentBuild.number} --width ${options.width} --height ${options.height} --engine ${options.engine} --iterations ${options.iterations} --scene_name \"${options.scene_name}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ").split('\r\n')[-1].trim()
				print("Preparing results")
			}
			render_service_send_render_status("Completed", options.id, options.django_url)

	    	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
		    	print(python3("send_viewer_results.py --django_ip \"${options.django_url}\" --build_number ${currentBuild.number} --status \"Success\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
	    	}
		} catch(e) {
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

    }   
    catch(e) {
		println(e.toString())
		println(e.getMessage())
		println(e.getStackTrace())
		print e
		if (fail_reason != "Expected exception") {
			render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, fail_reason)
		} else if (isLastAttempt) {
	    	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
		    	print(python3("send_viewer_results.py --django_ip \"${options.django_url}\" --build_number ${currentBuild.number} --status \"Failure\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
	    	}
		}
		throw e
    }
}



def main(String platforms, Map options) {
	 
	timestamps {
	    String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
	    String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
	    options['PRJ_PATH']="${PRJ_PATH}"
	    options['JOB_PATH']="${JOB_PATH}"

	    boolean PRODUCTION = true

		if (PRODUCTION) {
			options['django_url'] = "https://render.cis.luxoft.com/viewer/jenkins/"
			options['plugin_storage'] = "https://render.cis.luxoft.com/media/plugins/"
			options['scripts_branch'] = "master"
		} else {
			options['django_url'] = "https://testrender.cis.luxoft.com/viewer/jenkins/"
			options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
			options['scripts_branch'] = "develop"
		}

		List tokens = platforms.tokenize(':')
		String osName = tokens.get(0)
		String deviceName = tokens.get(1)

		String renderDevice = ""
		if (deviceName == "ANY") {
			renderDevice = "Viewer"
		} else {
			renderDevice = "gpu${deviceName}"
		}

		startRender(osName, deviceName, renderDevice, options)
	} 

}

def startRender(osName, deviceName, renderDevice, options) {
	def labels = "${osName} && RenderService && ${renderDevice}"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("Max attempts: ${options.max_attempts}")
	def maxAttempts = "${options.max_attempts}".toInteger()
	def testTasks = [:]
	def currentLabels = labels
	for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
		def currentNodeName = ""

		echo "Scheduling Build Viewer ${osName}:${deviceName}. Attempt #${attemptNum}"
		testTasks["Test-${osName}-${deviceName}"] = {
			node(labels){
				stage("BuildViewer-${osName}-${deviceName}"){
					timeout(time: 60, unit: 'MINUTES'){
						ws("WS/${options.PRJ_NAME}") {
							currentNodeName =  "${env.NODE_NAME}"
							try {
								boolean isLastAttempt = attemptNum == maxAttempts || attemptNum == nodesCount
								executeBuildViewer(osName, deviceName, attemptNum, isLastAttempt, options)
								successfullyDone = true
							} catch (e) {
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
	    
		if (successfullyDone) {
			break
		}
	}

	if (!successfullyDone) {
		if (nodesCount == 0) {
			// master machine can't access necessary nodes. Run notification script on any machine
			node("RenderService") {
				stage("Notify") {
					render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, 'No machine with specified configuration')
				}
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
    
def call(
    String scene_link = '',  
    String platforms = '',
    String viewer_version = '',
    String id = '',
    String width = '',
    String height = '',
    String engine = '',
    String iterations = '',
    String scene_name = '',
    String max_attempts = '',
    String timeout
    ) {
	String PRJ_ROOT='RenderServiceViewerJob'
	String PRJ_NAME='RenderServiceViewerJob'  
	main(platforms,[
	    enableNotifications:false,
	    PRJ_NAME:PRJ_NAME,
	    PRJ_ROOT:PRJ_ROOT,
	    scene_link:scene_link,
	    viewer_version:viewer_version,
	    id:id,
	    width:width,
	    height:height,
	    engine:engine,
	    iterations:iterations,
	    scene_name:scene_name,
	    max_attempts:max_attempts,
	    timeout:timeout
	    ])
    }

