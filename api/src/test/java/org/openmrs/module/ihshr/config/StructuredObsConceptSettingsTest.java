package org.openmrs.module.ihshr.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;
import org.openmrs.module.ihshr.utils.ChiefComplaintMatcher;
import org.openmrs.module.ihshr.utils.MedicalHistoryConstants;

public class StructuredObsConceptSettingsTest {
	
	@After
	public void clearOverrides() {
		System.clearProperty("ihshr.obs.chief_complaint.concept.ids");
	}
	
	@Test
	public void chiefComplaintConceptIds_loadsFromClasspathDefaults() {
		assertTrue(StructuredObsConceptSettings.chiefComplaintConceptIds().contains(
		    ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID));
		assertTrue(StructuredObsConceptSettings.chiefComplaintConceptIds().contains(5219));
	}
	
	@Test
	public void chiefComplaintMatcher_usesConfiguredConceptIds() {
		assertTrue(ChiefComplaintMatcher.matchesConceptId(ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID));
		assertTrue(ChiefComplaintMatcher.matchesConceptId(5219));
		assertFalse(ChiefComplaintMatcher.matchesConceptId(MedicalHistoryConstants.PATIENT_MEDICAL_HISTORY_CONCEPT_ID));
	}
	
	@Test
	public void chiefComplaintConceptIds_respectsSystemPropertyOverride() {
		System.setProperty("ihshr.obs.chief_complaint.concept.ids", "99999");
		assertEquals(1, StructuredObsConceptSettings.chiefComplaintConceptIds().size());
		assertTrue(StructuredObsConceptSettings.chiefComplaintConceptIds().contains(99999));
	}
	
}
