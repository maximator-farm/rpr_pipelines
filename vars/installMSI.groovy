def call(String msiName, String log)
{
    log = log ?: "${STAGE_NAME}"
    if (fileExists(msiName)){
        bat """
            msiexec /i "${msiName}" /quiet /qn /L+ie ${env.WORKSPACE}\\${log}_${options.currentTry}.msi.install.log /norestart
        """
    }else{
        echo "Missing msi ${msiName}"
    }
}
