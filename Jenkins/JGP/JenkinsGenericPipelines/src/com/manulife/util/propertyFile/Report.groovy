package com.manulife.util.propertyfile

import com.manulife.logger.Level
import com.manulife.util.AnsiColor
import com.manulife.util.AnsiText

import com.cloudbees.groovy.cps.NonCPS

/**
 * Reports on properties validation
 **/
class Report implements Serializable {
    /*
     * One report entry
     */

    enum ReportEntryStatus implements Serializable {
        ERROR('ERROR', AnsiColor.RED),
        WARNING('WARNING', AnsiColor.YELLOW),
        OK('OK', AnsiColor.GREEN)

        ReportEntryStatus(String desc, AnsiColor color) {
            this.desc = desc
            this.color = color
        }

        final String desc
        final AnsiColor color
    }

    class ReportEntry implements Serializable, Comparable<ReportEntry> {
        String propertyName
        ReportEntryStatus reportEntryStatus
        String source
        String value
        String detailedMessage

        @NonCPS
        int compareTo(ReportEntry other) {
            return this.propertyName <=> other.propertyName
        }
    }

    List<String> reportEntries = new ArrayList<String>()

    void addEntry(String propertyName, ReportEntryStatus reportEntryStatus, String source, String value, String detailedMessage) {
        def reportEntry = new ReportEntry()
        reportEntry.propertyName = propertyName
        reportEntry.reportEntryStatus = reportEntryStatus
        reportEntry.source = source
        reportEntry.value = value
        reportEntry.detailedMessage = detailedMessage
        reportEntries.add(reportEntry)
    }

    AnsiText getReport(Script scriptObj) {
        // Figure out if we want the compact or long version of the report
        Level loggingLevel = scriptObj.params.loggingLevel
        boolean longVersion = (loggingLevel in [Level.TRACE, Level.DEBUG])

        // Set max lengths to columnns titles
        int maxPropertyNameLength = 'Property Name'.length()
        int maxValueLength = 'Value'.length()
        int maxErrorWarningLength = (longVersion) ? 'Severity'.length() : 0
        int maxSourceLength = (longVersion) ? 'Source'.length() : 0
        int maxMessageLength = (longVersion) ? 'Message'.length() : 0

        // Look at report entries to figure out the larger values
        for (def reportEntry : reportEntries) {
            if (reportEntry.propertyName != null && reportEntry.propertyName.length() > maxPropertyNameLength) {
                maxPropertyNameLength = reportEntry.propertyName.length()
            }
            if (reportEntry.value != null && reportEntry.value.length() > maxValueLength) {
                maxValueLength = reportEntry.value.length()
            }
            if (longVersion) {
                if (reportEntry.reportEntryStatus != null && reportEntry.reportEntryStatus.desc.length() > maxErrorWarningLength) {
                    maxErrorWarningLength = reportEntry.reportEntryStatus.desc.length()
                }
                if (reportEntry.source != null && reportEntry.source.length() > maxSourceLength) {
                    maxSourceLength = reportEntry.source.length()
                }
                if (reportEntry.detailedMessage != null && reportEntry.detailedMessage.length() > maxMessageLength) {
                    maxMessageLength = reportEntry.detailedMessage.length()
                }
            }
        }

        AnsiText report = new AnsiText(scriptObj)
        String columnsSeparators = (longVersion) ? '|  |  |  |  |  |' : '|  |  |'
        int lineSize = columnsSeparators.length() + maxPropertyNameLength + maxErrorWarningLength + maxSourceLength + maxValueLength + maxMessageLength

        // Report Header
        String header = '-'.padRight(lineSize, '-') + '\n'
        header += '| ' + 'Property Name'.padRight(maxPropertyNameLength)
        if (longVersion) {
            header += ' | ' + 'Severity'.padRight(maxErrorWarningLength)
            header += ' | ' + 'Source'.padRight(maxSourceLength)
        }
        header += ' | ' + 'Value'.padRight(maxValueLength)
        if (longVersion) {
            header += ' | ' + 'Message'.padRight(maxMessageLength) + ' |'
        }
        header += '\n'
        header += '-'.padRight(lineSize, '-')
        report.addLine(header)

        // Report Detail lines
        Collections.sort(reportEntries)
        for (def reportEntry : reportEntries) {
            String reportLine = '| ' + fixValue(reportEntry.propertyName).padRight(maxPropertyNameLength)
            if (longVersion) {
                reportLine += ' | ' + fixValue(reportEntry.reportEntryStatus.desc).padRight(maxErrorWarningLength)
                reportLine += ' | ' + fixValue(reportEntry.source).padRight(maxSourceLength)
            }
            reportLine += ' | ' + fixValue(reportEntry.value).padRight(maxValueLength)
            if (longVersion) {
                reportLine += ' | ' + fixValue(reportEntry.detailedMessage).padRight(maxMessageLength)
            }

            if(!longVersion && reportLine.replaceFirst("\\s++\$", "").length() > 120) {
                reportLine = reportLine.substring(0, 117) + '...'
            }
            report.addLine(reportLine, reportEntry.reportEntryStatus.color)
        }

        // Report Footer
        String footer = '-'.padRight(lineSize, '-') + '\n'
        if(!longVersion) {
            footer += '-- Run this pipeline in DEBUG/TRACE mode for more details\n'
            footer += '-'.padRight(lineSize, '-') + '\n'
        }

        report.addLine(footer)

        return report
   }

   private String fixValue(String value) {
        if (value == null) {
            return 'null'
        }

        return value
   }
}