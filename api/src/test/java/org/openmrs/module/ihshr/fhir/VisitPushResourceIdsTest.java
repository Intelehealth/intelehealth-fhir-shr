package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;

public class VisitPushResourceIdsTest {
	
	@Test
	public void toFhirResourceId_replacesInvalidCharactersAndCapsLength() {
		String raw = "5a20c336-b14a-4c4d-a2be-f375cec77b76::topic-pregnancy-status";
		String id = VisitPushResourceIds.toFhirResourceId(raw);
		
		assertNotNull(id);
		assertTrue(id.matches("[A-Za-z0-9\\-.]+"));
		assertTrue(id.length() <= 64);
		assertTrue(!id.contains("::"));
	}
	
	@Test
	public void putUrl_usesSanitizedIdForObservationPutRequests() {
		Observation observation = new Observation();
		observation.addIdentifier(new Identifier().setSystem("urn:test").setValue(
		    "5a20c336-b14a-4c4d-a2be-f375cec77b76::topic-pregnancy-status"));
		
		String putUrl = VisitPushResourceIds.putUrl(observation);
		
		assertTrue(putUrl.startsWith("Observation/"));
		assertTrue(!putUrl.contains("::"));
	}
}
