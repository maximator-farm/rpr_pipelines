/**
* Pipeline to get log file from container logs output
*
* @param nodeName Jenkins node label
* @param containerName Docker container name
*/
def call(
    String nodeName,
    String containerName
) {
    node(nodeName) {
        stage("Logs getting") {
            sh "docker container logs ${containerName} 2>&1 >> ${containerName}.log";
        }
    }
}
