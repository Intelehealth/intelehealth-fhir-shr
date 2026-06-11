package org.openmrs.module.ihshr.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.hl7.fhir.r4.model.Coding;
import org.junit.Test;

public class ShrLookupLoaderTest {
	
	@Test
	public void loadChiefComplaintMapping_fromClasspath() {
		Coding fever = ShrLookupLoader.lookupMapping("chief-complaint-mappings.json", "Fever");
		assertNotNull(fever);
		assertEquals("386661006", fever.getCode());
	}
	
	@Test
	public void loadFamilyHistoryCondition_fromClasspath() {
		Coding diabetes = ShrLookupLoader.lookupMapping("family-history-conditions.json", "Diabetes");
		assertNotNull(diabetes);
		assertEquals("73211009", diabetes.getCode());
	}
	
	@Test
	public void loadExamBodySite_fromClasspath() {
		Coding site = ShrLookupLoader.lookupCategory("exam-bodysite-mappings.json", "General exams");
		assertNotNull(site);
		assertEquals("38266002", site.getCode());
	}
	
}
