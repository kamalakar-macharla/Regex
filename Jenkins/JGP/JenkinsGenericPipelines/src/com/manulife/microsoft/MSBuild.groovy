package com.manulife.microsoft

/**
  *
  * This class represents the MSBuild.exe tool
  *
  **/
class MSBuild implements Serializable {
    private final Script scriptObj
    String exe
    String buildType
    String buildOpts
    String rebuildCmd

    MSBuild(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    /**
     * Must be called on each MSBuild instance prior to using it
     **/
    void init(String msBuildVersion, String buildType) {
        exe = scriptObj.tool(msBuildVersion) + '\\msbuild.exe'

        this.buildType = ('debug' == buildType.toLowerCase()) ? 'Debug' : 'Release'
        buildOpts = "/p:Configuration=${this.buildType} /p:DebugType=Full -v:${scriptObj.logger.level.msBuildLevel}"

        rebuildCmd = "\"${exe}\" /t:rebuild ${buildOpts}"
    }
}