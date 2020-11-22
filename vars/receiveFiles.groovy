
def call(String remote, String local)
{
    withCredentials([string(credentialsId: 'buildsRemoteHost', variable: 'REMOTE_HOST')])
    {
        if(isUnix())
        {
            sh """
                ${CIS_TOOLS}/receiveFiles.sh \"${remote}\" ${local} ${REMOTE_HOST}
            """
        }
        else
        {
            bat """
                %CIS_TOOLS%\\receiveFiles.bat ${remote} ${local} ${REMOTE_HOST}
            """
        }
    }
}
