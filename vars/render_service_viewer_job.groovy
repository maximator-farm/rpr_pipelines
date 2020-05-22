def executeBuildViewer(osName, gpuName, Map options, uniqueID) {
    currentBuild.result = 'SUCCESS'
   
   	String scene_name = options['scene_name']
   	String fail_reason = "Unknown"
    echo "${options}"
    
	    try {
			print("Clean up work folder")
			cleanWs(deleteDirs: true, disableDeferredWipeout: true)

			// Download render service scripts
			try {
				print("Downloading scripts and install requirements")
				checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
				dir("install"){
					bat '''
					install_pylibs.bat
					'''
				}
			} catch(e) {
				currentBuild.result = 'FAILURE'
				print e
				fail_reason = "Downloading scripts failed"
			}

			dir("viewer_dir") {
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
					print(python3("..\\render_service_scripts\\send_viewer_status.py --django_ip \"${options.django_url}/\" --status \"Downloading viewer\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
				}
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsCredentials', usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_PASSWORD']]) {
					bat """
					curl --retry 3 -L -O -J -u %JENKINS_USERNAME%:%JENKINS_PASSWORD% "https://rpr.cis.luxoft.com/job/RadeonProViewerAuto/job/master/${options.viewer_version}/artifact/RprViewer_Windows.zip"
					"""
				}

				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
					print(python3("..\\render_service_scripts\\send_viewer_status.py --django_ip \"${options.django_url}/\" --status \"Downloading scene\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
					bat """
					curl --retry 3 -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.scene_link}"
					"""
				}
			}

			bat """
			copy "render_service_scripts\\send_viewer_results.py" "."
			copy "render_service_scripts\\configure_viewer.py" "."
			"""

			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
				print(python3("render_service_scripts\\send_viewer_status.py --django_ip \"${options.django_url}/\" --status \"Building RPRViewer Package\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
				python3("configure_viewer.py --version ${options.viewer_version} --width ${options.width} --height ${options.height} --engine ${options.engine} --iterations ${options.iterations} --scene_name \"${options.scene_name}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ").split('\r\n')[-1].trim()
				echo "Preparing results"
				print(python3("render_service_scripts\\send_viewer_status.py --django_ip \"${options.django_url}/\" --status \"Completed\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
			}
		    
	    }   
	    catch(e) {
	    	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
		    	print(python3("render_service_scripts\\send_viewer_status.py --django_ip \"${options.django_url}/\" --status \"Completed\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
			}
			currentBuild.result = 'FAILURE'
			print e
			echo "Error while configurating viewer"
	    } finally {
	    	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
		    	print(python3("send_viewer_results.py --django_ip \"${options.django_url}\" --build_number ${currentBuild.number} --status ${currentBuild.result} --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
	    	}
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

		startRender(platforms, options)

	} 

}

def startRender(platforms, options) {
	boolean successfullyDone = false

	def maxAttempts = "${options.max_attempts}".toInteger()
	def testTasks = [:]
	def nodes = platforms.split(';')
	int platformCount = nodes.size()
	def excludedLabels = ""

	tries: for (int attemptNum = 1; attemptNum <= maxAttempts; attemptNum++) {
		def currentNodeName = ""

		for (i = 0; i < platformCount; i++) {

		    String uniqueID = Integer.toString(i)

		    String item = nodes[i]
		    Map newOptions = options.clone()

		    List tokens = item.tokenize(':')
		    String osName = tokens.get(0)
		    String deviceName = tokens.get(1)
		    
		    String renderDevice = ""
		    if (deviceName == "ANY") {
				renderDevice = "Viewer"
		    } else {
			    renderDevice = "gpu${deviceName}"
		    }

		    def labels = "${osName} && RenderService && ${renderDevice}" + excludedLabels 

		    if (getNodesCount("${osName} && RenderService && ${renderDevice}") < attemptNum) {
		    	break tries
		    }

		    echo "Scheduling Build Viewer ${osName}:${deviceName}. Attempt #${attemptNum}"
		    testTasks["Test-${osName}-${deviceName}"] = {
				node(labels){
				    stage("BuildViewer-${osName}-${deviceName}"){
						timeout(time: 60, unit: 'MINUTES'){
						    ws("WS/${newOptions.PRJ_NAME}") {
								currentNodeName =  "${env.NODE_NAME}"
								try {
									executeBuildViewer(osName, deviceName, newOptions, uniqueID)
									successfullyDone = true
								} catch (e) {
									println(e.toString());
									println(e.getMessage());
									println(e.getStackTrace());
									print e

									//Exclude failed node name
									excludedLabels = excludedLabels + " && !" + currentNodeName
							    	println(currentLabels)
							    	currentBuild.result = 'SUCCESS'
								}
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
		throw new Exception("Job was failed by all used nodes!")
	}
}

def getNodesCount(labels) {
	def nodes = jenkins.model.Jenkins.instance.getLabel(labels).getNodes()
	def nodesCount = 0
	for (int i = 0; i < nodes.size(); i++) {
		if (nodes[i].toComputer().isOnline()) {
			nodesCount++
		}
	}

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

