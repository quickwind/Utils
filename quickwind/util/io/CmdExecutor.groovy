package quickwind.util.io

class CmdExecutor {
    static String exec(String cmdStr, String workingDir, Closure outputAppender, Closure erroutAppender, Closure errorHandler) {
        outputAppender?.call("Executing command: ${cmdStr}")
        def outputStr = new StringBuffer()
        def errorStr = new StringBuffer()
        def processMonitor = { inputStream, logAppender ->
            BufferedReader outbr = new BufferedReader(new InputStreamReader(inputStream))
            try {
                def outputline = null
                while ((outputline = outbr.readLine()) != null) {
                    if (outputline != null){
                        logAppender(outputline)                    
                    }
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
            finally{
                try {
                    inputStream.close()
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        }
        
        def finalOutputAppender = { line ->
            outputAppender?.call(line)
            outputStr.append(line).append(System.getProperty("line.separator"))
        }
        def finalErroutAppender = { line ->
            erroutAppender?.call(line)
            errorStr.append(line).append(System.getProperty("line.separator"))
        }
        
        Process procCcCmd = cmdStr.execute(null, new File(workingDir))
        
        def outMonitorT = Thread.start processMonitor.curry( procCcCmd.getInputStream(), finalOutputAppender )
        def errorMonitorT = Thread.start processMonitor.curry( procCcCmd.getErrorStream(), finalErroutAppender )
        
        procCcCmd.waitFor() 
        outMonitorT.join()
        errorMonitorT.join()    
        if(procCcCmd.exitValue() != 0){        
            erroutAppender?.call("Following command failed:")
            erroutAppender?.call("  ${cmdStr}")
            erroutAppender?.call("Error msg: " + errorStr)
            errorHandler()
        }
        
        outputAppender?.call("Executing command finished: ${cmdStr}")
        
        return outputStr.toString().replaceAll(/\s+$/, '')
    }
}

