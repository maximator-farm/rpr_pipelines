def main(Map options) {
	node (options.nodeLabels) {
		String deploymentFolder = options.folderName
		dir(deploymentFolder) {
			checkOutBranchOrScm(options['universeBranch'], 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
			dir("universe/${options.dockerScriptsDir}") {
				switch(options['mode']) {
					case 'Deploy':

						sh """
							./deploy-all.sh
						"""

						break;

					case "Update":

						sh """
							git checkout ${options.universeBranch}
						"""
						// TODO add checkout
						for (container in options.containers) {

							sh """
								./redeploy-${container.toLowerCase()}.sh .${options.dockerComposePostfix}
							"""
						}

						break;

					case "Undeploy":

						sh """
							./undeploy-all.sh
						"""

						break;

				}
			}
		}
	}
}

def call(
	String universeBranch = '',
	String nodeLabels = '',
	String dockerComposePostfix = '',
	String folderName = '',
	String mode = '',
	String containers = ''
	) {

	String RBSServicesRoot = "/home/admin/Server/RPRServers/rbs_auto_deploy"
	String dockerScriptsDir = "docker-management"

	main([
		universeBranch:universeBranch,
		nodeLabels:nodeLabels,
		dockerComposePostfix:dockerComposePostfix,
		folderName:folderName,
		mode:mode,
		RBSServicesRoot:RBSServicesRoot,
		dockerScriptsDir:dockerScriptsDir,
		containers:containers
		])
	}