/**
* Pipeline to deploy services that has such deploy structure {repoRoot}/deploy/{type}/docker-compose.yml
* As default works with gitlabURL credentials if gitServerUrl is not null
*
* @param project Name of project on git ( {gitServerUrl}/{repoGroup}/{project}.git )
* @param repoGroup Part of url means folder when repo stores ( {gitServerUrl}/{repoGroup}/{project}.git ) (It could be null)
* @param branch git branch name
* @param nodeName Jenkins node label
* @param type deploy type (same as dir name in your project deploy dir {repoRoot}/deploy/{type})
* @param gitUrlCredentialsId Jenkins credentialsId with string Url of git host (http://git.example.com)
* @param gitUserCredentialsId Jenkins credentialsId with user access data for git host
* @param composePath custom docker-compose file path from project root (if set, type is ingored) (some-dir/another/docker-compose.yml)
*/
def call(
    String project,
    String repoGroup,
    String branch,
    String nodeName,
    String type = "custom",
    String gitUrlCredentialsId = "gitlabURL",
    String gitUserCredentialsId = "radeonprorender-gitlab",
    String composePath = null
) {
    def gitServerUrl
    withCredentials([string(credentialsId: gitUrlCredentialsId, variable: "GIT_URL")]) {
        gitServerUrl = GIT_URL
    }

    def jnPath = "${type}/${project}"
    if (composePath == null) {
        composePath = "deploy/${type}/docker-compose.yml"
    }
    def urlParts = [ gitServerUrl, repoGroup, "${project}.git" ]
    def repositoryUrl = urlParts.findAll({ it != null }).join("/")

    println("[INFO] Project repository: ${repositoryUrl}")
    println("[INFO] Project branch: ${branch}")
    println("[INFO] Docker-compose file path: ${composePath}")

    node(nodeName) {
        dir(jnPath) {
            try {
                sh "sudo docker-compose -f ${composePath} stop";
                sh "sudo docker-compose -f ${composePath} rm --force"
            } catch(Exception e) {
                println("[WARNING] Catch an exception while docker stopping")
                println(e.toString())
                println(e.getMessage())
            }

            checkoutScm(branchName: branch, repositoryUrl: repositoryUrl, credentialsId: gitUserCredentialsId)

            stage('Build') {
                sh "sudo docker-compose -f ${composePath} build"
            }
            stage('Up') {
                sh "sudo docker-compose -f ${composePath} up -d"
            }
        }
    }
}
