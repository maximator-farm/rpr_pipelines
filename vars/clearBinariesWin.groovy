def call() {
    powershell"""
        if (Test-Path "${CIS_TOOLS}\\..\\PluginsBinaries") {
            forfiles /p "${CIS_TOOLS}\\..\\PluginsBinaries" /s /d -2 /c "cmd /c del @file"
            \$folderSize = (Get-ChildItem -Recurse \"${CIS_TOOLS}\\..\\PluginsBinaries\" | Measure-Object -Property Length -Sum).Sum / 1GB
            if (\$folderSize -ge 10) {
                Remove-Item -Recurse -Force \"${CIS_TOOLS}\\..\\PluginsBinaries\"
            }
        }
    """
}