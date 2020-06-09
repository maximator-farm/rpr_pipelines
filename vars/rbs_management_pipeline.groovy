def main(Map options) {
	String version
	if (options.universeBranch == "origin/master") {
		version = "master"
	} else {
		version = "develop"
	}

	String dockerComposeFile
	if (version == 'master') {
		dockerComposeFile = "docker-compose.yml"
	} else {
		dockerComposeFile = "docker-compose.dev.yml"
	}

	node('Ubuntu18 && Builder') {
		try {
			println("[INFO] Try to stop old RBS compose stack")
			sshagent(credentials : ['FrontendMachineCredentials']) {
				sh """
					ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe/docker-management/stop_pipeline.sh ${options.RBSServicesRoot}/${version}/universe/${dockerComposeFile}
					ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe/docker-management/remove_pipeline.sh ${options.RBSServicesRoot}/${version}/universe/${dockerComposeFile}
				"""
			}
			println("[INFO] Old RBS compose stack found and stopped")
		} catch (Exception e) {
			println("[INFO] Old RBS compose stack doesn't exist")
		}

		dir('universe') {
			checkOutBranchOrScm(options['universeBranch'], 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
		}

		sshagent(credentials : ['FrontendMachineCredentials']) {
			sh """
				ssh ${options.user}@${options.frontendIp} mkdir -p ${options.RBSServicesRoot}/${version}
				scp -r ./universe ${options.user}@${options.frontendIp}:${options.RBSServicesRoot}/${version}/universe

				ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe/docker-management/build_pipeline.sh ${options.RBSServicesRoot}/${version}/universe/${dockerComposeFile}
				ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe/docker-management/up_pipeline.sh ${options.RBSServicesRoot}/${version}/universe/${dockerComposeFile}
			"""
		}
	}
}

def call(
	String universeBranch = ''
	) {

	String RBSServicesRoot = "/home/admin/Server/RPRServers/rbs_auto_deploy"
	String user = "admin"
	String frontendIp = "172.30.23.112"

	main([
		universeBranch:universeBranch.toLowerCase(),
		RBSServicesRoot:RBSServicesRoot,
		user:user,
		frontendIp:frontendIp
		])
	}