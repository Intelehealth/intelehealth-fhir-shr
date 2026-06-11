package org.openmrs.module.ihshr.backlog;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnmappedTermArtifactTest {
	
	@Test
	public void fromLookupFile_chiefComplaint() {
		assertEquals(UnmappedTermArtifact.CHIEF_COMPLAINT,
		    UnmappedTermArtifact.fromLookupFile("chief-complaint-mappings.json"));
	}
	
	@Test
	public void fromLookupFile_medicalHistoryUsesSharedConditionsFile() {
		assertEquals(UnmappedTermArtifact.FAMILY_HISTORY_CONDITION,
		    UnmappedTermArtifact.fromLookupFile("family-history-conditions.json"));
	}
	
	@Test
	public void fromCode_physicalExam() {
		assertEquals(UnmappedTermArtifact.PHYSICAL_EXAM, UnmappedTermArtifact.fromCode("physical_exam"));
	}
	
}
