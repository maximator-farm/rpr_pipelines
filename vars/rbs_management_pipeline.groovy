def main(Map options) {
	String[] branchParts = options.universeBranch.split('/')
	// take last part after slash of branch name as version
	String version = branchParts[branchParts.length - 1]
	node('Ubuntu18 && Builder') {
		sshagent(credentials : ['FrontendMachineCredentials']) {
			sh "ssh admin@172.30.23.112 ls ${options.RBSServicesRoot}"
		}
	}
/*	dir(options.RBSServicesRoot) {
		if (fileExists("${version}")) {
			dir("${version}/universe") {
				sh """
					./stop.sh
					./remove.sh
				"""
			}
		}

		dir("${version}") {
			checkOutBranchOrScm(options['universeVersion'], 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)

			dir("universe") {
				if (version == 'master') {
					sh """
						./build.sh
						./up.sh
					"""
				} else {
					sh """
						./build.sh .dev
						./up.sh .dev
					"""
				}
			}
		}
	
	}*/
}

def call(
	String universeBranch = ''
	) {

	String RBSServicesRoot = "/home/admin/Server/RPRServers/rbs_auto_deploy"

	main([
		universeBranch:universeBranch.toLowerCase(),
		RBSServicesRoot:RBSServicesRoot
		])
	}