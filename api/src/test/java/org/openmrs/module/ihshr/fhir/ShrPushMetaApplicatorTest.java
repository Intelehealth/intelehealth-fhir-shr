package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;

public class ShrPushMetaApplicatorTest {
	
	@Test
	public void applyPushMeta_setsSourceAndTags() {
		Observation observation = new Observation();
		ShrPushMetaApplicator.applyPushMeta(observation, "https://intelehealth.org/openmrs/installation-12345");
		assertEquals("https://intelehealth.org/openmrs/installation-12345", observation.getMeta().getSource());
		assertEquals(2, observation.getMeta().getTag().size());
		assertEquals("intelehealth", observation.getMeta().getTag().get(0).getCode());
		assertEquals("frozen-at-source", observation.getMeta().getTag().get(1).getCode());
		assertTrue(observation.getMeta().getTag().get(0).getSystem().contains("intelehealth.org"));
	}
}
