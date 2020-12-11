import utils

def call(Map options, String lostTestsPath, String skippedTestsPath, String retryInfoPath, String engine = "")
{
    timeout(time: "10", unit: "MINUTES") {
        try {
            withCredentials([usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
                usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD'),
                string(credentialsId: 'prodUniverseURL', variable: 'PROD_UMS_URL'),
                string(credentialsId: 'devUniverseURL', variable: 'DEV_UMS_URL'),
                string(credentialsId: 'imageServiceURL', variable: 'IS_URL')])
            {
                String buildIdProd, jobIdProd, buildIdDev, jobIdDev
                if (engine) {
                    if (options.universeClientsProd[engine].build != null){
                        buildIdProd = options.universeClientsProd[engine].build["id"]
                        jobIdProd = options.universeClientsProd[engine].build["job_id"]
                    }
                    if (options.universeClientsDev[engine].build != null){
                        buildIdDev = options.universeClientsDev[engine].build["id"]
                        jobIdDev = options.universeClientsDev[engine].build["job_id"]
                    }
                } else {
                    if (options.universeClientProd.build != null){
                        buildIdProd = options.universeClientProd.build["id"]
                        jobIdProd = options.universeClientProd.build["job_id"]
                    }
                    if (options.universeClientDev.build != null){
                        buildIdDev = options.universeClientDev.build["id"]
                        jobIdDev = options.universeClientDev.build["job_id"]
                    }
                }

                withEnv(["UMS_BUILD_ID_PROD=${buildIdProd}", "UMS_JOB_ID_PROD=${jobIdProd}", "UMS_URL_PROD=${PROD_UMS_URL}", 
                    "UMS_LOGIN_PROD=${UMS_USER}", "UMS_PASSWORD_PROD=${UMS_PASSWORD}",
                    "UMS_BUILD_ID_DEV=${buildIdDev}", "UMS_JOB_ID_DEV=${jobIdDev}", "UMS_URL_DEV=${DEV_UMS_URL}",
                    "UMS_LOGIN_DEV=${UMS_USER}", "UMS_PASSWORD_DEV=${UMS_PASSWORD}",
                    "IS_LOGIN=${IS_USER}", "IS_PASSWORD=${IS_PASSWORD}", "IS_URL=${IS_URL}"])
                {
                    bat "send_stubs_to_ums.bat \"${skippedTestsPath}\" \"${lostTestsPath}\" \"${retryInfoPath}\""
                }
            }
        } catch (e) {
            if (utils.isTimeoutExceeded(e)) {
                println("[WARNING] Failed to send stubs to UMS due to timeout")
            } else {
                println("[WARNING] Failed to send stubs to UMS")
            }
            println(e.toString())
            println(e.getMessage())
        }
    }
}
