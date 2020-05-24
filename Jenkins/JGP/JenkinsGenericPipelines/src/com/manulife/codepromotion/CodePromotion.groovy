package com.manulife.codepromotion

import com.manulife.gitlab.GitLabCodePromotion
import com.manulife.gradle.BuildGradleFile
import com.manulife.maven.MavenPOMFile
import com.manulife.nodejs.PackageJsonFile
import com.manulife.microsoft.NuspecFile
import com.manulife.pipeline.PipelineType
import com.manulife.python.ParametersJsonFile
import com.manulife.versioning.IProjectVersioningFile
import com.manulife.versioning.SemVersion

class CodePromotion {
    private final Script scriptObj
    private final PipelineType pipelineType
    private final GitLabCodePromotion gitLabCodePromotion
    private IProjectVersioningFile projectVersioningFile
    private SemVersion currentVersion
    private final report = []

    CodePromotion(Script scriptObj, PipelineType pipelineType) {
        this.scriptObj = scriptObj
        this.pipelineType = pipelineType
        gitLabCodePromotion = new GitLabCodePromotion(scriptObj,
                                                      pipelineType,
                                                      "${scriptObj.GIT_URL}",
                                                      scriptObj.pipelineParams.gitLabSSHCredentialsId,
                                                      scriptObj.pipelineParams.fromBranch,
                                                      scriptObj.pipelineParams.toBranch)
    }

    void checkoutSourceVersion() {
        gitLabCodePromotion.checkoutSourceRepo()
        projectVersioningFile = getProjectVersioningFileInstance()
        projectVersioningFile.read()
        currentVersion = projectVersioningFile.getVersion()
        scriptObj.logger.info("current version in ${scriptObj.pipelineParams.fromBranch}: ${currentVersion}")
    }

    void updateVersionInSourceBranch() {
        def sourceVersion = currentVersion.getNextMinorVersion()
        projectVersioningFile.setVersion(sourceVersion)
        scriptObj.logger.info("New source version: ${sourceVersion}")
        projectVersioningFile.save()
        gitLabCodePromotion.commitSourceRepo(sourceVersion)
        report.add("Updated the version in the ${scriptObj.pipelineParams.fromBranch} branch to ${sourceVersion}.")
    }

    void updateVersionInDestinationBranch(boolean createNewBranch) {
        def destinationVersion = currentVersion
        if ('true' == scriptObj.pipelineParams.fromSnaphotToReleaseOnToBranch) {
            destinationVersion = destinationVersion.getReleaseVersion()
        }

        if ('true' == scriptObj.pipelineParams.increaseToBranchPatchVersion) {
            destinationVersion = destinationVersion.getNextPatchVersion()
        }
        scriptObj.logger.info("New ${scriptObj.pipelineParams.toBranch} version: ${destinationVersion}")

        if (createNewBranch) {
            gitLabCodePromotion.checkoutInNewDestinationBranch(destinationVersion)
        }
        else {
            gitLabCodePromotion.mergeAndTagInExistingDestinationBranch(destinationVersion)
        }

        boolean commitChanges = false
        if ('true' == scriptObj.pipelineParams.fromSnaphotToReleaseOnToBranch ||
            'true' == scriptObj.pipelineParams.increaseToBranchPatchVersion) {
            IProjectVersioningFile projectVersioningFile = getProjectVersioningFileInstance()
            projectVersioningFile.read()
            projectVersioningFile.setVersion(destinationVersion)
            projectVersioningFile.save()
            commitChanges = true
        }

        if (createNewBranch) {
            gitLabCodePromotion.commitAndPushToNewDestinationBranch(commitChanges, destinationVersion)
            report.add("Copied the ${scriptObj.pipelineParams.fromBranch} branch to a new branch called ${scriptObj.pipelineParams.toBranch}/${destinationVersion}.")
        }
        else {
            gitLabCodePromotion.commitPushAndTagInExistingDestinationBranch(commitChanges, destinationVersion)
            report.add("Merged the code from the ${scriptObj.pipelineParams.fromBranch} branch into the ${scriptObj.pipelineParams.toBranch} branch.")
            report.add("Updated the version in the ${scriptObj.pipelineParams.toBranch} branch to ${destinationVersion}.")
        }
    }

    String[] getReport() {
        return report
    }

    IProjectVersioningFile getProjectVersioningFileInstance() {
        switch (pipelineType) {
            case PipelineType.AEM_MAVEN:
                return new MavenPOMFile(scriptObj)
            case PipelineType.DOCKER:
                throw new IllegalArgumentException('Docker not supported by CodePromotion class.')
            case PipelineType.DOTNET:
            case PipelineType.DOTNETCORE:
                return new NuspecFile(scriptObj)
            case PipelineType.GO:
                throw new IllegalArgumentException('GoLang not supported by CodePromotion class.')
            case PipelineType.JAVA_GRADLE:
                // TODO: How can we tell is we should pass true/false as 2nd arg?
                return new BuildGradleFile(scriptObj, false)
            case PipelineType.JAVA_MAVEN:
                return new MavenPOMFile(scriptObj, '')
            case PipelineType.NODEJS:
                return new PackageJsonFile(scriptObj)
            case PipelineType.PYTHON:
                return new ParametersJsonFile(scriptObj)
            case PipelineType.SWIFT:
                throw new IllegalArgumentException('Swift not supported by CodePromotion class.')
            default:
                throw new IllegalArgumentException("${pipelineType} not supported by CodePromotion class.")
        }
    }
}