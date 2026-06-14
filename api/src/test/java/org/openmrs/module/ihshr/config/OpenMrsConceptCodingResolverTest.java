package org.openmrs.module.ihshr.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OpenMrsConceptCodingResolverTest {
	
	@Test
	public void mapSourceToSystem_mapsSnomedAndLoinc() {
		assertEquals("http://snomed.info/sct", OpenMrsConceptCodingResolver.mapSourceToSystem("SNOMED CT"));
		assertEquals("http://loinc.org", OpenMrsConceptCodingResolver.mapSourceToSystem("LOINC"));
	}
	
	@Test
	public void lookupByTerm_returnsNullWithoutOpenMrsSession() {
		assertNull(OpenMrsConceptCodingResolver.lookupByTerm("Fever"));
	}
	
}
