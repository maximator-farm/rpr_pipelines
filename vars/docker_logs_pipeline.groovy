/**
* Pipeline to get log file from container logs output
*
* @param nodeName Jenkins node label
* @param containerNames List of docker container names
*/
def call(
    String nodeName,
    List containerNames
) {
    node(nodeName) {
        stage("Logs getting") {
            cleanWS()
            containerNames.each{
                sh "(docker container logs ${it} 2>&1) >> ${it}.log";
            }
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        }
    }
}
