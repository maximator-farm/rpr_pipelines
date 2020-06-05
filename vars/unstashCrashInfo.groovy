def call(nodeRetryList){
    nodeRetryList.each{ gpu ->
        try
        {
            gpu['Tries'].each{ group ->
                group.each{ group, retries ->
                    retries.each{ retry ->
                        unstash "${retry['link']}"
                    }
                }
            }
        }catch(e)
        {
            echo "[ERROR] Failed to unstash ${it}"
            println(e.toString());
            println(e.getMessage());
        }
    }
}