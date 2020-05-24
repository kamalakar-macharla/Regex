package com.manulife.pipeline

/**
 *
 * Enum of all the pipeline types supported by the JGP.
 *
 **/
enum PipelineType implements Serializable {
    DOTNET(0, 'DotNet Classic'),
    DOTNETCORE(1, 'DotNetCore'),
    JAVA_MAVEN(2, 'Java Maven'),
    AEM_MAVEN(3, 'AEM Maven'),
    NODEJS(4, 'NodeJS'),
    SWIFT(5, 'Swift'),
    GO(6, 'Go'),
    JAVA_MAVEN_TEST(7, 'Java Maven Integration Test'),
    SHELLEXEC(8, 'Shell execution'),
    NIFI(9, 'Nifi'),
    SELENIUM(10, 'Selenium'),
    DOCKER(11, 'Docker'),
    FLYWAY(12, 'Flyway'),
    JAVA_GRADLE(13, 'Gradle'),
    PYTHON(14, 'Python'),
    EDGE_DEPLOY(15, 'Edge Deploy'),
    DEVTEST(16, 'DevTest'),
    SSRS(17, 'SSRS'),
    POSTMAN(18, 'Postman')

    private final int type
    private final String descr

    PipelineType(final int type, final String descr) {
        this.type = type
        this.descr = descr
    }

    static PipelineType lookup(String descr) {
        return (values().find { it.descr == descr } )
    }

    int getType() {
        return this.type
    }

    String getDescr() {
        return this.descr
    }
}
