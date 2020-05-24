package com.manulife.report

import com.manulife.util.AnsiColor
import com.manulife.util.AnsiText

/**
  *
  *  This class print a report showing if the Jenkins project is using the proper JGP version so that:
  *    1) It takes the source code from the CDT_Common/JenkinsGenericPipelines.git repository
  *    2) Uses the 'master' branch
  *
 **/
class SharedLibraryReport {
    private static final SEPARATOR = '**************************************************************************************'

    private final Script scriptObj

    SharedLibraryReport(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    void print() {
        try {
            AnsiText ansiText = new AnsiText(scriptObj)
            ansiText.addLine(SEPARATOR)
            ansiText.addLine('************************  Jenkins Generic Pipelines Version  *************************')
            ansiText.addLine(SEPARATOR)

            boolean found

            def buildDatas = scriptObj.currentBuild.rawBuild.getActions(hudson.plugins.git.util.BuildData)
            for (buildData in buildDatas) {
                // In theory someone could define multiple URls but there will usually be only one.
                for (remoteUrl in buildData.remoteUrls) {
                    if (remoteUrl.contains('JenkinsGenericPipelines.git')) {
                        // Make sure the project is using the CDT_Common Git Group
                        if (remoteUrl.contains('/CDT_Common/JenkinsGenericPipelines.git')) {
                            found = true
                            if (buildData.lastBuild.revision.containsBranchName('refs/remotes/origin/master')) {
                                ansiText.addLine('Using refs/remotes/origin/master JGP branch.', AnsiColor.GREEN)
                            }
                            else {
                                String branchName = buildData.lastBuild.revision.branches.iterator().next().name
                                ansiText.addLine("Using ${branchName} JGP branch.", AnsiColor.RED)
                                ansiText.addLine('IMPORTANT:  That version is not supported for production use.', AnsiColor.RED)
                                ansiText.addLine("IMPORTANT:  Please consider using 'master' branch instead.", AnsiColor.RED)
                            }
                        }
                        else if (remoteUrl.contains('{')) {
                            // Leverages the branch defined in the LibraryAction
                            found = false
                        }
                        else {
                            found = true
                            ansiText.addLine("Using wrong Git Repository: ${remoteUrl}", AnsiColor.RED)
                            ansiText.addLine('IMPORTANT:  That version is not supported for production use.', AnsiColor.RED)
                            ansiText.addLine("IMPORTANT:  Please consider using 'CDT_Common' repository instead.", AnsiColor.RED)
                        }
                    }
                }

                if (found) {
                    break
                }
            }

            // If no SCM yet defined or branch name (version) only defined in LibraryAction
            if (!found) {
                def librariesAction = scriptObj.currentBuild.rawBuild.getAction(org.jenkinsci.plugins.workflow.libs.LibrariesAction)
                if (librariesAction != null) {
                    for (library in librariesAction.libraries) {
                        found = true
                        if (library.version.contains('master')) {
                            ansiText.addLine("Using ${library.version} JGP branch.", AnsiColor.GREEN)
                        }
                        else {
                            ansiText.addLine("Using ${library.version} JGP branch.", AnsiColor.RED)
                            ansiText.addLine('IMPORTANT:  That version is not supported for production use.', AnsiColor.RED)
                            ansiText.addLine('''IMPORTANT:  Please consider using 'master' branch instead.''', AnsiColor.RED)
                        }
                    }
                }
            }

            if (!found) {
                ansiText.addLine('Unable to find pipelines version.', AnsiColor.RED)
            }

            ansiText.addLine(SEPARATOR)
            ansiText.printText()
        }
        catch (e) {
            scriptObj.echo("Exception while printing the SharedLibraryReport.  Message: ${e}.message")
        }
    }
}