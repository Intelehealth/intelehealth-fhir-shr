package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.parser.ChiefComplaintParserTest;
import org.openmrs.module.ihshr.parser.MedicalHistoryTopicConfig;
import org.openmrs.module.ihshr.parser.PhysicalExamParser;
import org.openmrs.module.ihshr.parser.PhysicalExamParserTest;
import org.openmrs.module.ihshr.utils.PhysicalExamConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints full SHR transaction-bundle JSON with doc §5.3 {@code meta.source} and {@code meta.tag} on
 * every resource (same shape as {@code DataSendToSHR} before POST). Run:
 * {@code mvn test -pl api -Dtest=ShrPushMetaPayloadTest#printFullShrPushPayloadsWithMeta} Save
 * output:
 * {@code mvn test -pl api -Dtest=ShrPushMetaPayloadTest#printFullShrPushPayloadsWithMeta 2> /tmp/shr-meta-payloads.log}
 */
public class ShrPushMetaPayloadTest {
	
	private static final String INSTALLATION_SOURCE_URI = "https://intelehealth.org/openmrs/installation-12345";
	
	private static final String LOOKUP_DIR = "/opt/intelehealth/shr-config";
	
	private static final String OBS_PE = "test-obs-163213";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void useOptLookupDir() {
		System.setProperty("ihshr.shr.lookup.dir", LOOKUP_DIR);
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
	}
	
	@Test
	public void printFullShrPushPayloadsWithMeta() throws Exception {
		System.err.println();
		System.err.println("================================================================");
		System.err.println(" SHR push payloads with meta (doc §5.3)");
		System.err.println(" meta.source = " + INSTALLATION_SOURCE_URI);
		System.err.println("================================================================");
		
		String samplePayload = printExportResourcePayloads();
		printStructuredObservationPayloads();
		
		assertMetaPresent(samplePayload);
		writeSampleFile(samplePayload);
	}
	
	private String printExportResourcePayloads() {
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# EXPORT FLOW (Location, Practitioner, Encounter, orders)");
		System.err.println("################################################################");
		
		Location location = new Location();
		location.setId("loc-uuid-001");
		location.setName("IH Clinic");
		location.setStatus(Location.LocationStatus.ACTIVE);
		
		Practitioner practitioner = new Practitioner();
		practitioner.setId("practitioner-uuid-001");
		practitioner.addName().setFamily("Singh").addGiven("Priya");
		
		Encounter encounter = new Encounter();
		encounter.setId("encounter-uuid-visit-complete");
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.getSubject().setReference("Patient/test-patient-uuid");
		encounter.getClass_().setCode("AMB");
		
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setId("service-request-uuid-001");
		serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
		serviceRequest.getSubject().setReference("Patient/test-patient-uuid");
		serviceRequest.getCode().setText("CBC");
		
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("medication-request-uuid-001");
		medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		medicationRequest.getSubject().setReference("Patient/test-patient-uuid");
		medicationRequest.getMedicationCodeableConcept().setText("Paracetamol 500mg");
		
		printPayloadWithMeta("Location export", location);
		printPayloadWithMeta("Practitioner export", practitioner);
		String encounterPayload = printPayloadWithMeta("Encounter (visit complete)", encounter);
		printPayloadWithMeta("ServiceRequest (lab)", serviceRequest);
		printPayloadWithMeta("MedicationRequest (drug)", medicationRequest);
		return encounterPayload;
	}
	
	private void printStructuredObservationPayloads() throws Exception {
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# STRUCTURED OBSERVATIONS (transferObservation)");
		System.err.println("################################################################");
		
		printChiefComplaintSample();
		printPhysicalExamSample();
	}
	
	private void printChiefComplaintSample() throws Exception {
		String valueJson = new JSONObject().put("en", ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC).toString();
		Observation source = sampleSourceObservation("Chief complaint");
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, "test-obs-163212", valueJson);
		int idx = 0;
		for (Condition condition : built.getConditions()) {
			printPayloadWithMeta("ChiefComplaint Condition " + (++idx), condition);
		}
		for (Observation obs : built.getAssociatedSymptomObservations()) {
			printPayloadWithMeta("ChiefComplaint associated Observation " + (++idx), obs);
		}
	}
	
	private String printPhysicalExamSample() {
		String valueJson = readTestDataLine("testdata/sample_Physical_Examination.json", 1);
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueJson);
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(valueJson);
		Observation source = sampleSourceObservation("Physical examination");
		PhysicalExamObservationBuilder builder = new PhysicalExamObservationBuilder();
		String lastPayload = null;
		int index = 0;
		for (ParsedExamCategory category : categories) {
			Observation categoryObs = builder.build(source, OBS_PE, category, sharedNote);
			lastPayload = printPayloadWithMeta("PhysicalExam category " + (++index) + " (" + category.getCategoryName()
			        + ")", categoryObs);
			assertTrue(categoryObs.getIdentifierFirstRep().getValue().startsWith(OBS_PE));
			assertTrue(lastPayload.contains(PhysicalExamConstants.EXAM_PROCEDURE_CODE));
		}
		return lastPayload;
	}
	
	private String printPayloadWithMeta(String label, Resource resource) {
		ShrPushMetaApplicator.applyPushMeta(resource, INSTALLATION_SOURCE_URI);
		Bundle bundle = toTransactionBundle(resource);
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		System.err.println();
		System.err.println("---------- " + label + " PUT " + resource.fhirType() + "/" + resolvePutResourceId(resource)
		        + " ----------");
		System.err.println(payload);
		System.err.println("---------- end " + label + " ----------");
		return payload;
	}
	
	private static void assertMetaPresent(String payload) {
		assertTrue("meta.source missing", payload.contains(INSTALLATION_SOURCE_URI));
		assertTrue("origin tag missing", payload.contains("\"code\" : \"intelehealth\""));
		assertTrue("frozen-at-source tag missing", payload.contains("\"code\" : \"frozen-at-source\""));
		assertTrue("meta.source field missing", payload.contains("\"source\""));
	}
	
	private static void writeSampleFile(String payload) throws Exception {
		File out = new File("target/test-output/shr-push-meta-sample-bundle.json");
		out.getParentFile().mkdirs();
		Files.write(out.toPath(), payload.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("Wrote sample bundle to: " + out.getAbsolutePath());
	}
	
	private static Bundle toTransactionBundle(Resource resource) {
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		Bundle.BundleEntryComponent entry = transactionBundle.addEntry();
		entry.setResource(resource);
		String resourceId = resolvePutResourceId(resource);
		entry.getRequest().setUrl(resource.fhirType() + "/" + resourceId).setMethod(Bundle.HTTPVerb.PUT);
		return transactionBundle;
	}
	
	private static String resolvePutResourceId(Resource resource) {
		if (resource instanceof Observation) {
			return ((Observation) resource).getIdentifierFirstRep().getValue();
		}
		if (resource instanceof Condition) {
			return ((Condition) resource).getIdentifierFirstRep().getValue();
		}
		return resource.getIdElement().getIdPart();
	}
	
	private static Observation sampleSourceObservation(String codeText) {
		Observation source = new Observation();
		source.setId("source-obs-id");
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.addPerformer(new Reference("Practitioner/test-practitioner-uuid"));
		source.getCode().setText(codeText);
		return source;
	}
	
	private static String readTestDataLine(String resource, int lineNumber) {
		InputStream in = ShrPushMetaPayloadTest.class.getClassLoader().getResourceAsStream(resource);
		if (in == null) {
			throw new IllegalStateException("Missing test resource: " + resource);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return reader.lines().skip(lineNumber - 1).findFirst().orElseThrow(IllegalStateException::new);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to read " + resource + " line " + lineNumber, e);
		}
	}
}
