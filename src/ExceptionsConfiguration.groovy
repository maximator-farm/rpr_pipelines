public class ExceptionsConfiguration {
    
    def static EMPTY_ENGINES_PARAM = [
         ["class": Exception, "problemMessage": "Engines parameter is required.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC]
    ]

    def static INITIALIZATION = [
         ["class": Exception, "problemMessage": "Failed initialization.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.GENERAL]
    ]

    def static DOWNLOAD_PLUGIN_REPO = [
         ["class": Exception, "problemMessage": "Failed to merge branches.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "getMessage": ["Branch not suitable for integration"],
         "githubNotification": ["status": "error"]],

         ["class": Exception, "problemMessage": "Failed to download plugin repository.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "error"]]
    ]

    def static INCREMENT_PLUGIN_VERSION = [
         ["class": Exception, "problemMessage": "Failed to increment plugin version.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "error"]]
    ]

    def static CONFIGURE_TESTS = [
         ["class": Exception, "problemMessage": "Failed to configurate tests.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "error"]]
    ]

    def static DOWNLOAD_JOBS_LAUNCHER = [
         ["class": "TimeoutExceeded", "problemMessage": "[WARNING] Failed to download jobs launcher due to timeout.", 
         "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC],

         ["class": Exception, "problemMessage": "[WARNING] Failed to download jobs launcher.", 
         "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC]
    ]

    def static BUILD_PLUGIN = [
         ["class": "TimeoutExceeded", "problemMessage": "Failed to download tests repository due to timeout.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,

         ["class": Exception, "problemMessage": "Failed to download tests repository.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER]
    ]

    def static DOWNLOAD_SCENES = [
         ["class": Exception, "problemMessage": "Failed to download test scenes.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER]
    ]

    def static DOWNLOAD_TESTS_REPO = [
         ["class": "TimeoutExceeded", "problemMessage": "[WARNING] Failed to download jobs launcher due to timeout.", 
         "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC],

         ["class": Exception, "problemMessage": "[WARNING] Failed to download jobs launcher.", 
         "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC]
    ]

    def static INSTALL_PLUGIN = [
         ["class": "TimeoutExceeded", "problemMessage": "Failed to install the plugin due to timeout.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,

         ["class": Exception, "problemMessage": "Failed to install the plugin.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER]
    ]

    def static BUILD_CACHE = [
         ["class": "TimeoutExceeded", "problemMessage": "Failed to build cache due to timeout.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,

         ["class": Exception, "problemMessage": "Failed to build cache.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER]
    ]

    def static EXECUTE_TESTS = [
         ["class": "TimeoutExceeded", "problemMessage": "Failed to execute tests due to timeout.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,

         ["class": Exception, "problemMessage": "An error occurred while executing tests. Please contact support.", 
         "rethrow": ExceptionThrowType.THROW_IN_WRAPPER]
    ]

    def static PUBLISH_REPORT = [
         ["class": Exception, "problemMessage": "Failed to publish test report.", 
         "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
         "githubNotification": ["status": "failure"]]
    ]

}
