package org.openmrs.module.ihshr.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DateUtilsTest {
	
	@Test
	public void toFhirLastUpdatedParam_convertsOpenMrsMarkerFormat() {
		assertEquals("2025-07-24T00:43:03", DateUtils.toFhirLastUpdatedParam("2025-07-24 00:43:03"));
	}
	
	@Test
	public void toFhirLastUpdatedParam_leavesIsoInstantUnchanged() {
		assertEquals("2025-07-24T00:43:03Z", DateUtils.toFhirLastUpdatedParam("2025-07-24T00:43:03Z"));
	}
	
	@Test
	public void toFhirLastUpdatedParam_handlesNullAndBlank() {
		assertNull(DateUtils.toFhirLastUpdatedParam(null));
		assertEquals("", DateUtils.toFhirLastUpdatedParam("   "));
	}
}
