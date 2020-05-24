package com.manulife.util.propertyfile

import org.testng.annotations.Test
import org.testng.Assert

import java.util.Properties

import com.manulife.logger.Level
import com.manulife.logger.Logger
import com.manulife.util.propertyfile.PropertiesCatalog
import com.manulife.util.propertyfile.PropertiesFileValidator

class PropertiesFileValidatorTest {
	static class MockScript extends Script {
		def logger = new Logger(this, Level.DEBUG)
		def params = [loggingLevel:'DEBUG']
		
		def ansiColor(String str, Closure cl) {
            cl()
        }

		Object run() {
			
		}
	}

	@Test
	void testMissingOptionalProperty() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		catalog.addOptionalProperty("optionalProperty", "Defaulting optionalProperty to defaultValue", "defaultValue")
		
		Properties properties = new Properties();
		
		PropertiesFileValidator validator = new PropertiesFileValidator(catalog)
		boolean valid = validator.validateProperties(properties)
		Assert.assertTrue(valid, "The validator should consider the properties as valid")
        String report = validator.getReportDetails(new MockScript()).getText()
		Assert.assertTrue(report.contains("Defaulting optionalProperty to defaultValue"))
		Assert.assertEquals(properties.getProperty("optionalProperty"), "defaultValue")
	}

	@Test
	void testProvidedOptionalProperty() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		
		catalog.addOptionalProperty("optionalProperty", "Defaulting optionalProperty to defaultValue", "defaultValue")
		
		Properties properties = new Properties();
		properties.setProperty("optionalProperty", "providedValue")
		
		PropertiesFileValidator validator = new PropertiesFileValidator(catalog)
		boolean valid = validator.validateProperties(properties)
		Assert.assertTrue(valid, "The validator should consider the properties as valid")
        String report = validator.getReportDetails(new MockScript()).getText()
		Assert.assertTrue(report.contains("Properties Files"))
		Assert.assertTrue(report.contains("providedValue"))
		Assert.assertEquals(properties.getProperty("optionalProperty"), "providedValue")
	}

	@Test
	void testMissingMandatoryProperty() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		
		catalog.addMandatoryProperty("mandatoryProperty", "Missing mandatory mandatoryProperty property")
		
		Properties properties = new Properties();
		
		PropertiesFileValidator validator = new PropertiesFileValidator(catalog);
		boolean valid = validator.validateProperties(properties)
		Assert.assertFalse(valid, "The validator should consider the properties as invalid")
		Assert.assertTrue(validator.getReportDetails(new MockScript()).getText().contains("Missing mandatory mandatoryProperty property"))
		Assert.assertNull(properties.getProperty("mandatoryProperty"))
	}

	@Test
	void testProvidedMandatoryProperty() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		
		catalog.addMandatoryProperty("mandatoryProperty", "Defaulting MandatoryProperty to defaultValue")
		
		Properties properties = new Properties();
		properties.setProperty("mandatoryProperty", "providedValue")
		
		PropertiesFileValidator validator = new PropertiesFileValidator(catalog)
		boolean valid = validator.validateProperties(properties)
		Assert.assertTrue(valid, "The validator should consider the properties as valid")
        String report = validator.getReportDetails(new MockScript()).getText()
		Assert.assertTrue(report.contains("Properties Files"))
		Assert.assertTrue(report.contains("providedValue"))
		Assert.assertEquals(properties.getProperty("mandatoryProperty"), "providedValue")
	}
}
