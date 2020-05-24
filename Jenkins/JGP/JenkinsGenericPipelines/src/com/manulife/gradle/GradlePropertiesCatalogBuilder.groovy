package com.manulife.gradle

import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.pipeline.PipelineType

/**
 *
 * Responsible to populate the properties catalog for Gradle CI pipelines.
 *
 **/
class GradlePropertiesCatalogBuilder {
    static build(PropertiesCatalog propertiesCatalog, PipelineType pipelineType) {
        propertiesCatalog.addOptionalProperty('buildGradleFileName',
                                              'Defaulting buildGradleFileName property to \"build.gradle\".  Should be set to \"build.gradle.kts\" for Kotlin projects.', 'build.gradle')
        propertiesCatalog.addOptionalProperty('gradleBuildTasks', 'Defaulting gradleBuildTasks property to \"--warning-mode all clean build\"', '--warning-mode all clean build')
        propertiesCatalog.addOptionalProperty('mavenSettingsFileName', 'Defaulting mavenSettingsFileName property to \"settings.xml\"', 'settings.xml')
        propertiesCatalog.addOptionalProperty('gradleTestTasks', 'Defaulting gradleTestTasks property to \"--warning-mode all clean test\"', '--warning-mode all clean test')
        propertiesCatalog.addOptionalProperty('useGradleWrapper', 'Defaulting useGradleWrapper property to \"false"', 'false')
        propertiesCatalog.addOptionalProperty('androidSdkHome', 'Defaulting androidSdkHome property to \"$HOME/Library/Android/sdk"', '$HOME/Library/Android/sdk')
    }
}
