package org.openmrs.module.ihshr.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.hl7.fhir.r4.model.Coding;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;

public class ClinicalTermCodingResolverTest {
	
	@Before
	public void setUp() {
		ShrLookupLoader.clearCache();
	}
	
	@Test
	public void lookupMapping_fallsBackToJsonWhenConceptDictionaryUnavailable() {
		Coding fever = ClinicalTermCodingResolver.lookupMapping("Fever", "chief-complaint-mappings.json");
		assertNotNull(fever);
		assertEquals("386661006", fever.getCode());
	}
	
	@Test
	public void resolveMapping_usesJsonLayerWhenDictionaryUnavailable() {
		Coding diabetes = ClinicalTermCodingResolver.resolveMapping("Diabetes", "family-history-conditions.json",
		    UnmappedTermArtifact.FAMILY_HISTORY_CONDITION);
		assertNotNull(diabetes);
		assertEquals("73211009", diabetes.getCode());
	}
	
	@Test
	public void resolveCategory_usesJsonLayerWhenDictionaryUnavailable() {
		Coding site = ClinicalTermCodingResolver.resolveCategory("General exams", "exam-bodysite-mappings.json",
		    UnmappedTermArtifact.PHYSICAL_EXAM_BODYSITE);
		assertNotNull(site);
		assertEquals("38266002", site.getCode());
	}
	
	@Test
	public void lookupMapping_returnsNullForUnknownTermWithoutSession() {
		assertNull(ClinicalTermCodingResolver.lookupMapping("Totally Unknown Clinical Term XYZ",
		    "chief-complaint-mappings.json"));
	}
	
}
