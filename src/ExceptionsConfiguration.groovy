public class ExceptionsConfiguration {
    
    def static BUILD_PLUGIN = [
         ["class": Exception, "problemMessage": "Failed to build the plugin.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "failure", "message": "Failed to build the plugin."]]
    ]

}
