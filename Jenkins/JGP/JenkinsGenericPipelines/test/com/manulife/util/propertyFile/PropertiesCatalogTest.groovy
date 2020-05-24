package com.manulife.util.propertyfile

import org.testng.annotations.Test
import org.testng.Assert

import com.manulife.util.propertyfile.OptionalProperty
import com.manulife.util.propertyfile.PropertiesCatalog

class PropertiesCatalogTest {
	@Test
	void testMandatoryPropertyAddAndRetrieve() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		catalog.addMandatoryProperty("name", "message");
		Assert.assertEquals(catalog.getPropertyDefinition("name").getName(), "name");		
		Assert.assertEquals(catalog.getPropertyDefinition("name").getMissingMessage(), "message");
	}

	@Test
	void testOptionalPropertyAddAndRetrieve() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		catalog.addOptionalProperty("name", "message", "defaultValue");
		Assert.assertEquals(catalog.getPropertyDefinition("name").getName(), "name", );
		Assert.assertEquals(catalog.getPropertyDefinition("name").getMissingMessage(), "message");
		Assert.assertEquals(((OptionalProperty)catalog.getPropertyDefinition("name")).getDefaultValue(), "defaultValue");
	}
	
	@Test
	void testgetPropertyDefinitions() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		catalog.addOptionalProperty("optionalPropertyName", "optionalmessage", "defaultValue");
		catalog.addMandatoryProperty("mandatoryPropertyName", "mandatorymessage");
		def properties = catalog.getPropertyDefinitions();
		Assert.assertEquals(catalog.getPropertyDefinitions().size(), 2);
		Assert.assertEquals(properties[0].getName(), "optionalPropertyName");
		Assert.assertEquals(properties[1].getName(), "mandatoryPropertyName");
	}
	
	@Test
	void testSize() {
		PropertiesCatalog catalog = new PropertiesCatalog();
		catalog.addOptionalProperty("optionalPropertyName", "optionalmessage", "defaultValue");
		catalog.addMandatoryProperty("mandatoryPropertyName", "mandatorymessage");
		Assert.assertEquals(catalog.size(), 2);
	}
}
