package com.manulife.microsoft

/**
 * Utility class that can be used to fix MS project's name
 **/
class ProjectName {
    static final fix(String propertyName, Properties properties) {
        if (properties[propertyName]?.endsWith('.sln')) {
            properties[propertyName] = properties[propertyName][0..-5]
        }
    }
}