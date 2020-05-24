package com.manulife.gradle

import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

/**
 *
 * Utility class to interact with a build.gradle file.
 *
 **/
class BuildGradleFile implements IProjectVersioningFile, Serializable {
    private final Script scriptObj
    private final boolean useGradleWrapper

    // TODO: Optimize this class.  We could read the file once and keep and
    //       manipulate its content in memory

    BuildGradleFile(Script scriptObj, boolean useGradleWrapper) {
        this.scriptObj = scriptObj
        this.useGradleWrapper = useGradleWrapper
    }

    @Override
    void read() {
        // Implement when optimizing this class
    }

    @Override
    void save () {
        // Implement when optimizing this class
    }

    @Override
    SemVersion getVersion() {
        scriptObj.logger.info('Reading current project version...')
        def unix = scriptObj.isUnix()

        def gradleScript = ''
        if (useGradleWrapper) {
            gradleScript = './gradlew'
            gradleScript += (unix ? '' : '.bat')
        }
        else {
          gradleScript = 'gradle'
        }

        def versionStr = ''
        if (unix) {
            versionStr = scriptObj.sh returnStdout: true, script: gradleScript + ' properties -q | grep version:'
        }
        else {
            versionStr = scriptObj.bat returnStdout: true, script: gradleScript + ' properties -q | findstr -i "version:"'

            // versionStr will look like this at this point (it is including the command line itself and an empty line):
            //
            // e:\jenkins-old-farm\workspace\Example_Projects\Example_1_2\Example_Gradle\Example_Gradle_Dev_CI>gradle properties -q   | findstr -i "version:"
            // version: 0.1.5

            def strTokens = versionStr.split('\n')

            scriptObj.logger.debug("strTokens = ${strTokens}")

            // Set versionStr to "version: 0.1.5\n" if we use the same example
            versionStr = strTokens[2]
        }

        scriptObj.logger.debug("versionStr: ${versionStr}")

        // Split into ["version", "0.1.5\n"]
        def versionStrTokens = versionStr.split(': ')

        scriptObj.logger.debug("versionStrTokens: ${versionStrTokens}")

        // Set versionStr to "0.1.5\n" if using same example
        versionStr = versionStrTokens[1]

        scriptObj.logger.debug("versionStr: *${versionStr}*")

        // Remove the carriage returns from the end of version number, if any
        versionStr = versionStr.replaceAll('\\n', '')
        versionStr = versionStr.replaceAll('\\r', '')

        scriptObj.logger.debug("versionStr: *${versionStr}*")

        def version = SemVersion.parse(scriptObj, versionStr)
        scriptObj.logger.info("Version: ${version.toString()}")
        return version
    }

    @Override
    void setVersion(SemVersion newProjectVersion) {
        def oldProjectVersion = getVersion()
        scriptObj.logger.info("Updating project version from ${oldProjectVersion.toString()} to ${newProjectVersion.toString()}")
        String fileContent = scriptObj.readFile file: "${scriptObj.pipelineParams.buildGradleFileName}", encoding: 'utf-8'
        scriptObj.logger.debug("fileContent BEFORE: ${fileContent}")

        fileContent = fileContent.replaceAll("version = \"${oldProjectVersion.toString()}\"", "version = \"${newProjectVersion.toString()}\"")
        scriptObj.logger.debug("fileContent AFTER: ${fileContent}")

        scriptObj.writeFile file: "${scriptObj.pipelineParams.buildGradleFileName}", text: fileContent, encoding: 'utf-8'
    }

    String getGroup() {
        scriptObj.logger.info('Reading current project group...')
        return extractFromBuildFile(scriptObj, 'group', useGradleWrapper)
    }

    String getJarBaseName() {
        scriptObj.logger.info('Reading current project group...')
        if (scriptObj.pipelineParams.buildGradleFileName.contains('.kts')) {
            return extractFromBuildFile(scriptObj, 'rootProject.name', useGradleWrapper)
        } else {
            return extractFromBuildFile(scriptObj, 'jarBaseName', useGradleWrapper)
        }
    }

    String extractFromBuildFile(String fieldName) {
        def unix = scriptObj.isUnix()
        def gradleScript = ''
        def field = ''

        //Determine whether to use the gradle wrapper
        if (useGradleWrapper) {
            gradleScript = './gradlew'
            gradleScript += (unix ? '' : '.bat')
        }
        else {
            gradleScript = 'gradle'
        }

        //Get the field string based on unix env or windows env
        if (scriptObj.isUnix()) {
            //field = scriptObj.sh returnStdout: true, script: "cat ${scriptObj.pipelineParams.buildGradleFileName} |  grep {version =} | cut -d'=' -f2 | sed 's/"/g' | tr -d ' '`
            if (fieldName == 'rootProject.name') {
                field = scriptObj.sh returnStdout: true, script: "cat settings.gradle.kts | grep \"${fieldName} = [^,]*\"".trim()
            } else {
                field = scriptObj.sh returnStdout: true, script: "cat ${scriptObj.pipelineParams.buildGradleFileName} | grep \"${fieldName} = [^,]*\"".trim()
            }
        }
        else {
            field = scriptObj.bat returnStdout: true, script: gradleScript + " properties -q | findstr -i \'|\"${fieldName} = [^,]*\'|\""
            def strTokens = field.split('\n')
            scriptObj.logger.debug("strTokens = ${strTokens}")
            field = strTokens[2]
        }

        //Split based on quotes and remove newlines and carriage returns
        field = field.split("\'|\"")[1]
        field = field.replaceAll('\\n', '')
        field = field.replaceAll('\\r', '')
        scriptObj.logger.info("${field}")
        return field
    }
}
