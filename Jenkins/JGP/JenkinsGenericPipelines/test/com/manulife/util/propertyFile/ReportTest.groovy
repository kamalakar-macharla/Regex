package com.manulife.util.propertyfile

import org.testng.annotations.Test
import org.testng.Assert

class ReportTest {
    @Test
    void testReportEntryCompareToSmaller_HappyPath() {
        Report.ReportEntry entry = new Report.ReportEntry()
        entry.propertyName = "aaa"
        Report.ReportEntry otherEntry = new Report.ReportEntry()
        otherEntry.propertyName = "zzz"
        Assert.assertTrue(entry.compareTo(otherEntry) < 0)
    }

    @Test
    void testReportEntryCompareToEqual_HappyPath() {
        Report.ReportEntry entry = new Report.ReportEntry()
        entry.propertyName = "aaa"
        Report.ReportEntry otherEntry = new Report.ReportEntry()
        otherEntry.propertyName = "aaa"
        Assert.assertTrue(entry.compareTo(otherEntry) == 0)
    }

    @Test
    void testReportEntryCompareToLarger_HappyPath() {
        Report.ReportEntry entry = new Report.ReportEntry()
        entry.propertyName = "zzz"
        Report.ReportEntry otherEntry = new Report.ReportEntry()
        otherEntry.propertyName = "aaa"
        Assert.assertTrue(entry.compareTo(otherEntry) > 0)
    }
}
