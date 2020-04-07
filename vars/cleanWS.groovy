def call(String osName = "Windows") {
    try {
        // kill blender, maya and max processes on Windows for prevent locks
        try {
            if (osName == "Windows") {
                bat '''
                    taskkill /f /im "blender.exe"
                    taskkill /f /im "maya.exe"
                    taskkill /f /im "3dsmax.exe"
                '''
            }
        } catch (Exception e) {
            // ignore errors if processes don't exist
        } 
        println("[INFO] Try to clean WS via cleanWs command")
        cleanWs(deleteDirs: true, disableDeferredWipeout: true)
        println("[INFO] WS was successfully cleaned via cleanWs command")
    } catch (Exception e1) {
        try {
            println("[ERROR] WS cleaning via cleanWs command failed. Try to do it via deleteDir command")
            deleteDir()
            println("[INFO] WS was successfully cleaned via deleteDir command")
        } catch (Exception e2) {
            println("[ERROR] WS cleaning via deleteDir command failed. Try to do it via OS commands")
            switch(osName) {
                case 'Windows':
                    bat '''
                        @echo off
                        del /q *
                        for /d %%x in (*) do @rd /s /q "%%x"
                    '''
                    break
                default:
                    sh '''
                        rm -rf *
                    '''
            }

            println("[INFO] WS was successfully cleaned via OS commands")
        }
    }
}
