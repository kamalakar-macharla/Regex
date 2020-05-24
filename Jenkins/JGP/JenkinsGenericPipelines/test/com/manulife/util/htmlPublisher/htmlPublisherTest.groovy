package com.manulife.util.htmlpublisher

import org.testng.annotations.Test
import org.testng.Assert

import com.manulife.logger.Level
import com.manulife.logger.Logger

import com.manulife.util.htmlpublisher.HtmlPublisher
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertiesFileValidator

class HtmlPublisherTest {
    static class MockScript extends Script {
        def logger = new Logger(this, Level.INFO)
        Object run() {
            println('Running...')
        }

        def echo(String message) {
            println(message)
        }

        def publishHTML(def opts) {
            println(opts.target)
        }
    }

	@Test
	void testMandatoryPropertyAddAndRetrieve() {
	
		
		Properties properties = new Properties()
		properties.setProperty("htmlReportNames", "Integration Test Report|adasdsa")
		properties.setProperty("htmlReportRelativePaths", "index.html|jndex.html")
		properties.setProperty("htmlReportFiles", "allure-report|bllure-report")
		
		new HtmlPublisher(new MockScript(), properties).publish()

		Assert.assertEquals(0,0);
	}
	@Test
	void testMissingField() {
	
		
		Properties properties = new Properties()
		properties.setProperty("htmlReportNames", "Integration Test Report|adasdsa")
		properties.setProperty("htmlReportRelativePaths", "index.html|jndex.html")
		
		new HtmlPublisher(new MockScript(), properties).publish()

		Assert.assertEquals(0,0);
	}

	@Test
	void testShortageOnList() {
	
		
		Properties properties = new Properties()
		properties.setProperty("htmlReportNames", "Integration Test Report|adasdsa|report2")
		properties.setProperty("htmlReportRelativePaths", "index.html|jndex.html")		
		properties.setProperty("htmlReportFiles", "allure-report|bllure-report")
		
		new HtmlPublisher(new MockScript(), properties).publish()

		Assert.assertEquals(0,0);
	}
}