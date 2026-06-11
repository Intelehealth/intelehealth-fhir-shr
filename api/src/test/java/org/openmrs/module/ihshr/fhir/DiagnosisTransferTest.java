package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;

public class DiagnosisTransferTest {
	
	@Test
	public void build_primaryAndSecondary_shouldSetExpectedRanks() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult primary = transfer.build(source, "obs-primary", "82272006::Acute rhinitis:Primary & Confirmed",
		    3);
		DiagnosisBuildResult secondary = transfer.build(source, "obs-secondary",
		    "J30.0::Acute rhinitis:Secondary & Provisional", 3);
		
		assertNotNull(primary);
		assertNotNull(secondary);
		assertEquals(Integer.valueOf(1), primary.getRank());
		assertEquals(Integer.valueOf(2), secondary.getRank());
	}
	
	@Test
	public void build_unknownType_shouldUseFallbackRank() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult unknown = transfer.build(source, "obs-unknown", "{\"diagnosis\":\"Viral fever\"}", 5);
		
		assertNotNull(unknown);
		assertEquals(Integer.valueOf(5), unknown.getRank());
	}
	
	@Test
	public void build_withoutPerformer_shouldUseEncounterParticipantAsAsserter() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		Encounter encounter = new Encounter();
		encounter.setId("test-encounter");
		encounter.addParticipant().setIndividual(new Reference("Practitioner/doctor-uuid"));
		
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult built = transfer.build(source, "obs-primary", "82272006::Acute rhinitis:Primary & Confirmed",
		    3, encounter, null);
		
		assertNotNull(built);
		assertTrue(built.getCondition().hasAsserter());
		assertEquals("Practitioner/doctor-uuid", built.getCondition().getAsserter().getReference());
	}
	
	@Test
	public void build_withoutPerformer_shouldUseParentEncounterParticipantAsAsserter() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/child-encounter"));
		
		Encounter childEncounter = new Encounter();
		childEncounter.setId("child-encounter");
		childEncounter.setPartOf(new Reference("Encounter/parent-encounter"));
		
		Encounter parentEncounter = new Encounter();
		parentEncounter.setId("parent-encounter");
		parentEncounter.addParticipant().setIndividual(new Reference("Practitioner/visit-doctor"));
		
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult built = transfer.build(source, "obs-primary", "82272006::Acute rhinitis:Primary & Confirmed",
		    3, childEncounter, parentEncounter);
		
		assertNotNull(built);
		assertEquals("Practitioner/visit-doctor", built.getCondition().getAsserter().getReference());
	}
}
