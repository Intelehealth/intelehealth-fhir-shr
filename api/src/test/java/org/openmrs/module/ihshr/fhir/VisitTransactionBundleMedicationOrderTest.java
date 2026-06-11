package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;

/**
 * SHR transaction bundles require referenced Medication to appear before MedicationRequest.
 */
public class VisitTransactionBundleMedicationOrderTest {
	
	private static final String PATIENT_UUID = "3c2a29ff-370c-49fd-9310-09e8fa123347";
	
	@Test
	public void prependedMedicationAppearsBeforeMedicationRequest() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-medication-order",
		        (bundle, addedCruids, subject, logPrefix) -> {
			        Reference rewritten = new Reference("urn:uuid:ih-shr-patient-" + PATIENT_UUID);
			        ShrCruidPatientSupport.rewriteSubjectReference(subject, rewritten.getReference());
			        return true;
		        });
		
		Medication medication = new Medication();
		medication.setId("e4dfbc37-24cd-416d-8e56-cc44bc1f4dc2");
		medication.getCode().setText("Telmisartan 20mg");
		
		MedicationRequest rx = new MedicationRequest();
		rx.setId("09a809d7-4b2c-4886-8d26-6726581747a6");
		rx.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		rx.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		rx.getSubject().setReference("Patient/" + PATIENT_UUID);
		rx.getMedicationReference().setReference("Medication/e4dfbc37-24cd-416d-8e56-cc44bc1f4dc2");
		
		builder.addPutResource(rx, "[MedicationRequest]");
		assertTrue(builder.addPutResource(medication, "[Medication]", true));
		
		Bundle bundle = builder.build();
		int medicationIndex = -1;
		int requestIndex = -1;
		for (int i = 0; i < bundle.getEntry().size(); i++) {
			String type = bundle.getEntry().get(i).getResource().fhirType();
			if ("Medication".equals(type)) {
				medicationIndex = i;
			}
			if ("MedicationRequest".equals(type)) {
				requestIndex = i;
			}
		}
		assertTrue(medicationIndex >= 0);
		assertTrue(requestIndex >= 0);
		assertTrue("Medication must be ordered before MedicationRequest in transaction bundle",
		        medicationIndex < requestIndex);
	}
}
