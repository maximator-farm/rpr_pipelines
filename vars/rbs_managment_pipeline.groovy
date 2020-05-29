def main(Map options) {
	node (options.nodeLabels) {
		String deploymentFolder = options.RBSServicesRoot + "/" + options.folderName
		dir (deploymentFolder) {
			ssh """
				pwd;
			"""
		}
	}
}

def call(
	String universeBranch = '',
	String nodeLabels = '',
	String dockerComposePostfix = '',
	String folderName = '',
	String mode = ''
	) {

	String RBSServicesRoot = "/home/admin/Server/RPRServers/rbs_auto_deploy"

	main([
		universeBranch:universeBranch,
		nodeLabels:nodeLabels,
		dockerComposePostfix:dockerComposePostfix,
		folderName:folderName,
		mode:mode,
		RBSServicesRoot:RBSServicesRoot
		])
	}