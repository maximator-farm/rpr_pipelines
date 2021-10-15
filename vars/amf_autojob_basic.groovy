import jenkins.model.Jenkins

def call() {
	LUXSDK_AUTOJOB_CONFIG = [
      'projectBranch' :             'origin/amd/stg/amf',
      'testsBranch' :               'origin/master',
      'platforms' :                 'Windows:Navi23',
      'clientTag' :                 'LuxSDK_Client',
      'winBuildConfiguration' :     'release',
      'winVisualStudioVersion' :    '2019',
      'winTestingBuildName' :       'release_vs2019',
      'androidBuildConfiguration' : 'release,debug',
      'games' :                     'LoL,HeavenDX9,HeavenDX11,ValleyDX11,ValleyDX9',
      'testsPackage' :              'General.json',
      'tests' :                     'General',
      'testerTag' :                 'StreamingSDK',
      'testCaseRetries' :           2,
      'clientCollectTraces' :       false,
      'serverCollectTraces' :       false,
      'storeOnNAS' :                false
    ]

    print(str(LUXSDK_AUTOJOB_CONFIG))
        
}