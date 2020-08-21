class utils {

    static int getTimeoutFromXML(Object self, String test, String keyword, Integer additional_xml_timeout) 
    {
        try {
            String xml = self.readFile("jobs/Tests/${test}/test.job-manifest.xml")
            for (xml_string in xml.split("<")) {
                if (xml_string.contains("${keyword}")) {
                    Integer xml_timeout = (Math.round((xml_string.findAll(/\d+/)[0] as Double).div(60)) + additional_xml_timeout)
                    return xml_timeout
                }
            }
        } catch (e) {
            self.println(e)
            return -1
        }
        
    }

    static def publishReport(Object self, String buildUrl, String reportDir, String reportFiles, String reportName, String reportTitles)
    {
        self.publishHTML([allowMissing: false,
                     alwaysLinkToLastBuild: false,
                     keepAll: true,
                     reportDir: reportDir,
                     reportFiles: reportFiles,
                     // TODO: custom reportName (issues with escaping)
                     reportName: reportName,
                     reportTitles: reportTitles])
        try {
            self.httpRequest(
                url: "${buildUrl}/${reportName.replace(' ', '_20')}/",
                authentication: 'jenkinsCredentials',
                httpMode: 'GET'
            )
            self.println("[INFO] Report exists")
        } catch(e) {
            self.println("[ERROR] Can't access report")
            throw new Exception("Can't access report", e)
        }
    }

}