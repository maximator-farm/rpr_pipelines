import groovy.json.JsonBuilder

def call(String status, String id, String django_ip, String fail_reason = '') {
    Map body = [:]
    body.status = status
    body.id = id
    if (fail_reason) {
    	body.fail_reason = fail_reason
    }

    String bodyJson = new JsonBuilder(body).toPrettyString()

    httpRequest(
    	url: django_ip,
    	authentication: 'renderServiceCredentials',
    	requestBody: bodyJson,
    	httpMode: 'POST'
    )
}
