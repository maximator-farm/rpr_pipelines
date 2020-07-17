import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String pluginLink, String pluginHash, String tool, String version, String id, String django_url) {
	// get tool name without plugin name
	String toolName = tool.split(' ')[0].trim()

	Boolean sceneExists
	switch(toolName) {
		case 'Blender':
			sceneExists = fileExists "${CIS_TOOLS}/../PluginsBinaries/${pluginHash}_Windows.zip"
			break;

		default:
			sceneExists = fileExists "${CIS_TOOLS}/../PluginsBinaries/${pluginHash}_Windows.msi"
			break;
	}

	Map installationOptions = [
		'isPreBuilt': true,
		'stageName': 'RenderServiceRender',
		'customBuildLinkWindows': pluginLink
	]

	if (sceneExists) {
		switch(toolName) {
			case 'Blender':
				bat """
					move "${CIS_TOOLS}\\..\\PluginsBinaries\\${pluginHash}_Windows.zip" ${pluginHash}_Windows.zip
				"""
				break;

			default:
				bat """
					move "${CIS_TOOLS}\\..\\PluginsBinaries\\${pluginHash}_Windows.msi" ${pluginHash}_Windows.msi
				"""
				break;
		}

		installationOptions['pluginWinSha'] = pluginHash
	} else {
		try {
			render_service_send_render_status("Downloading plugin", id, django_url)
			clearBinariesWin()

			downloadPlugin('Windows', toolName, installationOptions, 'renderServiceCredentials')
			String win_addon_name = installationOptions.pluginWinSha

			switch(toolName) {
				case 'Blender':
					bat """
						IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
						move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${win_addon_name}.zip"
					"""
					break;

				default:
					bat """
						IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
						move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${win_addon_name}.msi"
					"""
					break;
			}
		} catch(FlowInterruptedException e) {
			throw e
		} catch(e) {
			fail_reason = "Plugin downloading failed"
			throw e
		}
	}

	try {
		render_service_send_render_status("Installing plugin", id, django_url)
		Boolean installationStatus = null

		switch(toolName) {
			case 'Blender':
				installationStatus = installBlenderAddon('Windows', version, installationOptions)
				break;

			default:
				installationStatus = installMSIPlugin('Windows', toolName, installationOptions)
				break;
		}

		print "[INFO] Install function return ${installationStatus}"
	} catch(FlowInterruptedException e) {
		throw e
	} catch(e) {
		fail_reason = "Plugin installation failed"
		throw e
	}
}
