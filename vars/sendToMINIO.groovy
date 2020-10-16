import utils

def call(Map options, String osName, String filesPath, String pattern)
{
    timeout(time: "3", unit: 'MINUTES') {
        try {
            withCredentials([string(credentialsId: 'minioEndpoint', variable: 'MINIO_ENDPOINT'),
                usernamePassword(credentialsId: 'minioService', usernameVariable: 'MINIO_ACCESS_KEY', passwordVariable: 'MINIO_SECRET_KEY')])
            {
                withEnv(["UMS_BUILD_ID_PROD=${options.buildIdProd}", "UMS_JOB_ID_PROD=${options.jobIdProd}",
                    "UMS_BUILD_ID_DEV=${options.buildIdDev}", "UMS_JOB_ID_DEV=${options.jobIdDev}",
                    "MINIO_ENDPOINT=${MINIO_ENDPOINT}", "MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}", "MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"])
                {
                    switch(osName) {
                        case "Windows":
                            filesPath = filesPath.replace('/', '\\\\')
                            bat "send_to_minio.bat \"${filesPath}\" \"${pattern}\""
                            break;
                        default:
                            sh """
                                chmod u+x send_to_minio.sh
                                ./send_to_minio.sh ${filesPath} \"${pattern}\"
                            """
                    }
                }
            }
        } catch (e) {
            if (utils.isTimeoutExceeded(e)) {
                println("[WARNING] Failed to send files to MINIO due to timeout")
            } else {
                println("[WARNING] Failed to send files to MINIO")
            }
            println(e.toString())
            println(e.getMessage())
        }
    }
}
