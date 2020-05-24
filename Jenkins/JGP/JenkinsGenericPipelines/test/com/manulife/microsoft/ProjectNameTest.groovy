package com.manulife.microsoft

import net.sf.json.JSONSerializer;
import org.testng.annotations.Test
import org.testng.Assert

class ProjectNameTest {
    @Test
    void testFix_NoPropertiesAtAll() {
        Properties properties = new Properties()
        ProjectName.fix('does-not-exits', properties)
        Assert.assertEquals(properties.size(), 0)
    }

    @Test
    void testFix_NoSuchProperty() {
        Properties properties = new Properties()
        properties.setProperty('doesnt_match', 'dont_care')
        ProjectName.fix('does-not-exits', properties)
        Assert.assertEquals(properties.size(), 1)
        Assert.assertEquals(properties['doesnt_match'], 'dont_care')
        Assert.assertEquals(properties['does-not-exits'], null)
    }

    @Test
    void testFix_PropertyExists_NoNeedToFix() {
        Properties properties = new Properties()
        properties.setProperty('name', 'value')
        ProjectName.fix('name', properties)
        Assert.assertEquals(properties.size(), 1)
        Assert.assertEquals(properties['name'], 'value')
    }

    @Test
    void testFix_PropertyExists_NeedsToBeFixed() {
        Properties properties = new Properties()
        properties.setProperty('name', 'value.sln')
        ProjectName.fix('name', properties)
        Assert.assertEquals(properties.size(), 1)
        Assert.assertEquals(properties['name'], 'value')
    }
}