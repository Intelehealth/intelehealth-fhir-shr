package org.openmrs.module.ihshr.fhir.provenance;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.fhir.ShrCruidPatientSupport;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;
import org.openmrs.module.ihshr.utils.CruidConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Run: {@code mvn test -pl api -Dtest=ShrProvenancePayloadTest#printDiagnosisProvenanceBundle}
 */
public class ShrProvenancePayloadTest {
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printDiagnosisProvenanceBundle() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		Set<String> addedCruids = new HashSet<>();
		ShrCruidPatientSupport.addPatientEntryIfAbsent(bundle, addedCruids, "CR-2026-000001");
		
		Condition condition = new Condition();
		condition.setId("cond-rhinitis");
		condition.getSubject().setReference("urn:uuid:ih-shr-patient-CR-2026-000001");
		bundle.addEntry().setResource(condition).getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Condition/cond-rhinitis");
		
		Encounter encounter = new Encounter();
		encounter.setId("encounter-uuid");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.getSubject().setReference("urn:uuid:ih-shr-patient-CR-2026-000001");
		bundle.addEntry().setResource(encounter).getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Encounter/encounter-uuid");
		
		ShrProvenanceBundleSupport.appendProvenanceEntry(bundle, ProvenanceAssertionClass.DIAGNOSES, "visit-uuid-1",
		    Arrays.asList("Condition/cond-rhinitis", "Encounter/encounter-uuid"), new java.util.Date(),
		    new java.util.Date(), "diagnosis-obs-uuid", ShrProvenanceReferences.doctorAuthorReference());
		ShrPushMetaApplicator.applyPushMeta(bundle);
		
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		System.err.println(payload);
		assertTrue(payload.contains(CruidConstants.IDENTIFIER_SYSTEM));
		assertTrue(payload.contains("visit-uuid-1--prov-diagnosis"));
		assertTrue(payload.contains("\"resourceType\" : \"Provenance\""));
	}
}
