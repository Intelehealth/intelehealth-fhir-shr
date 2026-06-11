package org.openmrs.module.ihshr.fhir;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;

public class ShrFhirValidatorTest {
	
	@Test
	public void validateOrThrow_shouldPassForValidPatient() {
		Patient p = new Patient();
		p.addName().setFamily("Rahman").addGiven("Amina");
		
		new ShrFhirValidator().validateOrThrow(p);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void validateOrThrow_shouldFailForInvalidObservation() {
		// Observation without required fields (e.g., status and code) must fail.
		Observation obs = new Observation();
		
		new ShrFhirValidator().validateOrThrow(obs);
	}
}
