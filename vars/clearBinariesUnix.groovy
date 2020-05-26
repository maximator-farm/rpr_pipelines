def call()
{
    sh """
        if [ -d "${CIS_TOOLS}/../PluginsBinaries" ]; then
            find "${CIS_TOOLS}/../PluginsBinaries" -mtime +2 -delete
            find "${CIS_TOOLS}/../PluginsBinaries" -size +50G -delete
        fi
    """
}