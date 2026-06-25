package org.openmrs.module.ihshr.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShrFhirUrlSupportTest {
	
	@Test
	public void normalizesLegacyFhirBaseToShrFhir() {
		assertEquals("http://192.168.19.152:6001/shr/fhir/",
		    ShrFhirUrlSupport.normalizeShrFhirBaseUrl("http://192.168.19.152:6001/fhir/"));
		assertEquals("http://192.168.19.152:6001/shr/fhir/",
		    ShrFhirUrlSupport.normalizeShrFhirBaseUrl("http://192.168.19.152:6001/fhir"));
	}
	
	@Test
	public void keepsShrFhirBaseUnchanged() {
		assertEquals("http://192.168.19.152:6001/shr/fhir/",
		    ShrFhirUrlSupport.normalizeShrFhirBaseUrl("http://192.168.19.152:6001/shr/fhir/"));
	}
}
