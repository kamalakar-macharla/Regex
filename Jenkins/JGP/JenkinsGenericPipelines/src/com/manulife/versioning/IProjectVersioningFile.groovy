package com.manulife.versioning

interface IProjectVersioningFile {
    void read()
    void save()
    SemVersion getVersion()
    void setVersion(SemVersion version)
}