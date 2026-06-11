package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.CruidConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints final SHR transaction-bundle JSON as produced by {@code DataSendToSHR} after CRUID
 * linking. Run:
 * {@code mvn test -pl api -Dtest=ShrCruidPushPayloadTest#printFinalShrPushPayloadsWithCruid}
 */
public class ShrCruidPushPayloadTest {
	
	private static final String SAMPLE_CRUID = "CR-2026-000001";
	
	private static final String OPENMRS_PATIENT_UUID = "5e4b57c2-7f0b-4d63-a8bb-0c8b534d6546";
	
	private static final String INSTALLATION_SOURCE_URI = "https://intelehealth.org/openmrs/installation-12345";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFinalShrPushPayloadsWithCruid() throws Exception {
		System.err.println();
		System.err.println("================================================================");
		System.err.println(" Final SHR push payloads (Patient ifNoneExist + CRUID + meta)");
		System.err.println("================================================================");
		
		String encounterPayload = printTransferPayload("Encounter (visit complete)", sampleEncounter());
		printTransferPayload("Observation (vital)", sampleObservation());
		printTransferPayload("ServiceRequest (lab order)", sampleServiceRequest());
		printTransferPayload("MedicationRequest (drug order)", sampleMedicationRequest());
		
		assertTrue(encounterPayload.contains(CruidConstants.IDENTIFIER_SYSTEM));
		assertTrue(encounterPayload.contains(SAMPLE_CRUID));
		assertTrue(encounterPayload.contains("ifNoneExist"));
		
		java.io.File out = new java.io.File("target/test-output/shr-cruid-encounter-bundle.json");
		out.getParentFile().mkdirs();
		Files.write(out.toPath(), encounterPayload.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("Wrote encounter sample to: " + out.getAbsolutePath());
	}
	
	private String printTransferPayload(String label, Resource clinicalResource) {
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		Set<String> addedCruids = new HashSet<>();
		
		Reference subject = ShrCruidPatientSupport.extractSubjectReference(clinicalResource);
		subject.setReference("Patient/" + OPENMRS_PATIENT_UUID);
		
		String patientFullUrl = ShrCruidPatientSupport.addPatientEntryIfAbsent(transactionBundle, addedCruids,
		    SAMPLE_CRUID);
		ShrCruidPatientSupport.rewriteSubjectReference(subject, patientFullUrl);
		
		ShrPushMetaApplicator.applyPushMeta(clinicalResource, INSTALLATION_SOURCE_URI);
		Bundle.BundleEntryComponent clinicalEntry = transactionBundle.addEntry();
		clinicalEntry.setResource(clinicalResource);
		clinicalEntry.getRequest().setMethod(Bundle.HTTPVerb.PUT)
		        .setUrl(clinicalResource.fhirType() + "/" + clinicalResource.getIdElement().getIdPart());
		
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle);
		System.err.println();
		System.err.println("---------- " + label + " ----------");
		System.err.println(payload);
		System.err.println("---------- end " + label + " ----------");
		return payload;
	}
	
	private static Encounter sampleEncounter() {
		Encounter encounter = new Encounter();
		encounter.setId("encounter-uuid-visit-complete");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		encounter.getClass_().setCode("AMB");
		return encounter;
	}
	
	private static Observation sampleObservation() {
		Observation observation = new Observation();
		observation.setId("observation-uuid-vital");
		observation.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		observation.getCode().setText("Body weight");
		observation.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		return observation;
	}
	
	private static ServiceRequest sampleServiceRequest() {
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setId("service-request-uuid-001");
		serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
		serviceRequest.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		serviceRequest.getCode().setText("CBC");
		return serviceRequest;
	}
	
	private static MedicationRequest sampleMedicationRequest() {
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("medication-request-uuid-001");
		medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		medicationRequest.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		medicationRequest.getMedicationCodeableConcept().setText("Paracetamol 500mg");
		return medicationRequest;
	}
}
