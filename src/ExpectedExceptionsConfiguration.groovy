public class ExpectedExceptionsConfiguration {
    
    def static BUILD_PLUGIN = [
         ["class": Exception, "problemMessage": "Failed to build the plugin.", 
         "rethrow": ExpectedExecutionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "failure", "message": "Failed to build the plugin."]]
    ]

}
