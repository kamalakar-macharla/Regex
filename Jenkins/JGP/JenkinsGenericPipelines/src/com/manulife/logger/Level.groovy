package com.manulife.logger

/**
 *
 * Represents the different available logging levels.
 *
 **/
enum Level implements Serializable {
    TRACE(  0, 'TRACE',   'TRACE',   '-X', 'TRACE', '', 'd'),
    DEBUG(  1, 'DEBUG',   'INFO',    '-X', 'DEBUG', '', 'd'),
    INFO(   2, 'INFO',    'WARNING', '-q', 'WARN',  '', 'm'),
    WARNING(3, 'WARNING', 'WARNING', '-q', 'WARN',  '', 'm'),
    ERROR(  4, 'ERROR',   'ERROR',   '-q', 'ERROR', '', 'q'),
    FATAL(  5, 'FATAL',   'FATAL',   '-q', 'FATAL', '', 'q'),
    OFF(    6, 'OFF',     'OFF',     '-q', 'OFF',   '-q', 'q')

    Level(int value, String name, String sonarQubeLevel, String mvnLevel, String hubDetectLevel, String snykLevel, String msBuildLevel) {
        this.value = value
        this.name = name
        this.sonarQubeLevel = sonarQubeLevel
        this.mvnLevel = mvnLevel
        this.hubDetectLevel = hubDetectLevel
        this.snykLevel = snykLevel
        this.msBuildLevel = msBuildLevel
    }

    private final int value
    private final String name
    private final String sonarQubeLevel
    private final String mvnLevel
    private final String hubDetectLevel
    private final String snykLevel
    private final String msBuildLevel

    int getValue() {
        return value
    }

    String getName() {
        return name
    }

    String getSonarQubeLevel() {
        return sonarQubeLevel
    }

    String getMvnLevel() {
        return mvnLevel
    }

    String getHubDetectLevel() {
        return hubDetectLevel
    }

    String getSnykLevel() {
        return snykLevel
    }

    String getMSBuildLevel() {
        return msBuildLevel
    }
}
