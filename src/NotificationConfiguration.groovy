public class ExceptionsConfiguration {
    
    def static ENGINES_PARAM = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Engines parameter is required.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static INITIALIZATION = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed initialization.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.GENERAL
            ]
        ]
    ]

    def static DOWNLOAD_PLUGIN_REPO = [
        "begin": ["message": "Downloading plugin repository."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to merge branches.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": ["Branch not suitable for integration"],
                "githubNotification": ["status": "error"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to download plugin repository.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "error"]
            ]
        ]
    ]

    def static INCREMENT_PLUGIN_VERSION = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to increment plugin version.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "error"]
            ]
        ]
    ]

    def static CONFIGURE_TESTS = [
        "end": ["message": "PreBuild stage was successfully finished."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to configurate tests.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "error"]
            ]
        ]
    ]

    def static DOWNLOAD_JOBS_LAUNCHER = [
        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "[WARNING] Failed to download jobs launcher due to timeout.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ],
            [
                "class": Exception, "problemMessage": "[WARNING] Failed to download jobs launcher.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static BUILD_PLUGIN = [
        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to download tests repository due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ],
            [
                "class": Exception, "problemMessage": "Failed to download tests repository.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static DOWNLOAD_SCENES = [
        "begin": ["message": "Downloading test scenes."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to download test scenes.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static DOWNLOAD_TESTS_REPO = [
        "begin": ["message": "Downloading tests repository."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to download tests repository due to timeout.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ],
            [
                "class": Exception, "problemMessage": "Failed to download tests repository.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static INSTALL_PLUGIN = [
        "begin": ["message": "Installing the plugin."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to install the plugin due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ],
            [
                "class": Exception, "problemMessage": "Failed to install the plugin.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static BUILD_CACHE = [
        "begin": ["message": "Building cache."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to build cache due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ],
            [
                "class": Exception, "problemMessage": "Failed to build cache.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static COPY_BASELINES = [
        "begin": ["message": "Downloading reference images."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "[WARNING] Problem when copying baselines.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static EXECUTE_TESTS = [
        "begin": ["message": "Executing tests."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to execute tests due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ],
            [
                "class": Exception, "problemMessage": "An error occurred while executing tests. Please contact support.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static PUBLISH_REPORT = [
        "begin": ["message": "Publishing test report."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to publish test report.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static PRE_BUILD_STAGE_FAILED = "PreBuild stage was failed."

    def static BUILDING_PLUGIN = "Building the plugin."

    def static PLUGIN_BUILT = "The plugin was successfully built and published."

    def static REASON_IS_NOT_IDENTIFIED = "The reason is not automatically identified. Please contact support."

    def static SOME_TESTS_ERRORED = "Some tests were marked as error. Check the report for details."

    def static SOME_TESTS_FAILED = "Some tests were marked as failed. Check the report for details."

    def static ALL_TESTS_PASSED = "Tests completed successfully."

    def static FAILED_TO_SAVE_RESULTS = "An error occurred while saving test results. Please contact support."

    def static BUILDING_REPORT = "Building test report."

    def static REPORT_PUBLISHED = "Report was published successfully."

    def static TIMEOUT_EXCEEDED = "Timeout exceeded."

    def static UNKNOWN_REASON = "Unknown reason."

    def static STAGE_TIMEOUT_EXCEEDED = "Stage timeout exceeded."

    def static BUILD_ABORTED_BY_COMMIT = "Build was aborted by new commit."

    def static BUILD_ABORTED_BY_USER = "Build was aborted by user."

    def static LOST_CONNECTION_WITH_MACHINE = "Lost connection with machine. Please contact support."

}
