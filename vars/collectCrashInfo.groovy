def call(osName, logName){
    println "Collect crash logs"
    switch(osName){
        case 'Windows':
            powershell """
                echo ${env.NODE_NAME} >> ${logName}.crash.log
                Get-Date >> ${logName}.crash.log
                Get-EventLog -LogName System -Newest 200 >> ${logName}.crash.log
                Get-EventLog -LogName Application -Newest 200 >> ${logName}.crash.log
                Get-EventLog -LogName HardwareEvents -Newest 200 >> ${logName}.crash.log
                ps | sort -des cpu | select -f 200 | ft -a >> ${logName}.crash.log
            """
            break;
        case 'OSX':
            sh """
                echo ${env.NODE_NAME} >> ${logName}.crash.log
                date >> ${logName}.crash.log
                log show --no-info --color always --predicate 'eventMessage CONTAINS[c] "radeon" OR eventMessage CONTAINS[c] "gpu" OR eventMessage CONTAINS[c] "amd"' --last 1h >> ${logName}.crash.log
                top -l 1 | head -n 200 >> ${logName}.crash.log
                sudo iotop | head -n 200 >> ${logName}.crash.log
            """
            break;
        default:
            sh """
                echo ${env.NODE_NAME} >> ${logName}.crash.log
                date >> ${logName}.crash.log
                dmesg | tail -n 200 >> ${logName}.crash.log
                top -b | head -n 200 >> ${logName}.crash.log
                iotop --only -b | head -n 200 >> ${logName}.crash.log
            """
            break;
    }
    archiveArtifacts artifacts: "*.crash.log"
    stash includes: "${logName}.crash.log", name: "${logName}"
}