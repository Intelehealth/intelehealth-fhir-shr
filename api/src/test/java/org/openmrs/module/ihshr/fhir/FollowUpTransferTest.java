package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.FollowUpConstants;

public class FollowUpTransferTest {
	
	@Test
	public void build_sampleFollowUp_shouldProduceObservation() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		FollowUpBuildResult built = new FollowUpTransfer().build(source, "obs-follow-up-1",
		    "2026-06-11,Time:10:00 AM,Remark:NA,Type:In person");
		
		assertTrue(built.hasObservation());
		Observation observation = built.getObservation();
		assertEquals(Observation.ObservationStatus.FINAL, observation.getStatus());
		assertEquals("Follow up date", observation.getCode().getText());
		assertEquals("In person", observation.getValueStringType().getValueAsString());
		assertEquals(FollowUpConstants.observationIdentifier("obs-follow-up-1"), observation.getIdentifierFirstRep()
		        .getValue());
		assertTrue(observation.hasEffectiveDateTimeType());
		assertEquals(3, observation.getComponent().size());
		assertNotNull(observation.getSubject());
		assertNotNull(observation.getEncounter());
	}
	
	@Test
	public void build_noValue_shouldSkip() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		FollowUpBuildResult built = new FollowUpTransfer().build(source, "obs-follow-up-2", "No");
		assertFalse(built.hasObservation());
	}
	
}
