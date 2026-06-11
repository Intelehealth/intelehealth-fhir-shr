package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass;
import org.openmrs.module.ihshr.parser.ChiefComplaintParserTest;
import org.openmrs.module.ihshr.parser.MedicalHistoryTopicConfig;
import org.openmrs.module.ihshr.parser.PhysicalExamParser;
import org.openmrs.module.ihshr.parser.PhysicalExamParserTest;
import org.openmrs.module.ihshr.utils.CruidConstants;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;
import org.openmrs.module.ihshr.utils.ImageObsConstants;

import ca.uhn.fhir.context.FhirContext;

/**
 * Builds one complete visit-scoped SHR {@code transaction} bundle (doc §5) using the same
 * {@link VisitTransactionBundleBuilder} as production, then writes pretty JSON to
 * {@code target/test-output/visit-complete-push-bundle.json}.
 * <p>
 * Run:
 * 
 * <pre>
 * mvn test -pl api -Dtest=VisitPushPayloadTest#printCompleteVisitPushBundle
 * </pre>
 */
public class VisitPushPayloadTest {
	
	private static final String LOOKUP_DIR = "/opt/intelehealth/shr-config";
	
	private static final String VISIT_UUID = "visit-uuid-sample-001";
	
	private static final String SAMPLE_CRUID = "CR-2026-000001";
	
	private static final String OPENMRS_PATIENT_UUID = "5e4b57c2-7f0b-4d63-a8bb-0c8b534d6546";
	
	private static final String ENC_VISIT_COMPLETE = "encounter-visit-complete";
	
	private static final String ENC_CHILD = "encounter-child-consult";
	
	private static final String INSTALLATION_SOURCE_URI = "https://intelehealth.org/openmrs/installation-12345";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void useOptLookupDir() {
		System.setProperty("ihshr.shr.lookup.dir", LOOKUP_DIR);
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
	}
	
	@Test
	public void printCompleteVisitPushBundle() throws Exception {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder(VISIT_UUID, cruidSupport());
		
		addEncounter(builder, sampleEncounter(ENC_VISIT_COMPLETE, "Visit complete"), true);
		addEncounter(builder, sampleEncounter(ENC_CHILD, "Outpatient consult"), false);
		
		addVitalObservation(builder);
		addChiefComplaint(builder);
		addPhysicalExam(builder);
		addFamilyHistory(builder);
		addMedicalHistory(builder);
		addDiagnosis(builder);
		addOrders(builder);
		addImage(builder);
		
		Bundle bundle = builder.build();
		ShrPushMetaApplicator.applyPushMeta(bundle, INSTALLATION_SOURCE_URI);
		
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# VISIT-SCOPED SHR PUSH — complete transaction bundle");
		System.err.println("# visit_uuid=" + VISIT_UUID + " entries=" + bundle.getEntry().size());
		System.err.println("################################################################");
		System.err.println(payload);
		
		Path out = Paths.get("target/test-output/visit-complete-push-bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, payload.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("Wrote complete visit bundle to: " + out.toAbsolutePath());
		
		assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
		assertTrue(bundle.getEntry().size() >= 10);
		assertTrue(payload.contains(CruidConstants.IDENTIFIER_SYSTEM));
		assertTrue(payload.contains("\"resourceType\" : \"Provenance\""));
		assertTrue(payload.contains("ifNoneExist"));
	}
	
	private VisitPushCruidSupport cruidSupport() {
		return (transactionBundle, addedCruids, subject, logPrefix) -> {
			if (subject == null || !subject.hasReference()) {
				return false;
			}
			String openMrsPatientUuid = ShrCruidPatientSupport.extractOpenMrsPatientUuid(subject.getReference());
			if (openMrsPatientUuid == null) {
				subject.setReference("Patient/" + OPENMRS_PATIENT_UUID);
				openMrsPatientUuid = OPENMRS_PATIENT_UUID;
			}
			String patientFullUrl = ShrCruidPatientSupport.addPatientEntryIfAbsent(transactionBundle, addedCruids,
			    SAMPLE_CRUID);
			ShrCruidPatientSupport.rewriteSubjectReference(subject, patientFullUrl);
			return true;
		};
	}
	
	private void addEncounter(VisitTransactionBundleBuilder builder, Encounter encounter, boolean visitCompletion) {
		builder.addPutResource(encounter, "[Encounter]");
		if (visitCompletion) {
			builder.registerProvenanceTarget(ProvenanceAssertionClass.VISIT_COMPLETION, encounter, null);
		}
	}
	
	private void addVitalObservation(VisitTransactionBundleBuilder builder) {
		Observation vital = new Observation();
		vital.setId("obs-vital-weight");
		vital.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		vital.getEncounter().setReference("Encounter/" + ENC_CHILD);
		vital.getCode().setText("Body weight");
		vital.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		vital.addIdentifier(new Identifier().setValue("obs-vital-weight"));
		builder.addPutResource(vital, "[Observation]");
		builder.registerProvenanceTarget(ProvenanceAssertionClass.VITALS, vital, null);
	}
	
	private void addChiefComplaint(VisitTransactionBundleBuilder builder) throws Exception {
		String valueJson = new JSONObject().put("en", ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC).toString();
		Observation source = sampleSourceObservation("Chief complaint", "obs-cc-source");
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, "obs-cc-source", valueJson);
		addClinicalResources(builder, built.getConditions(), built.getAssociatedSymptomObservations(), "obs-cc-source",
		    ProvenanceAssertionClass.CHIEF_COMPLAINT);
	}
	
	private void addPhysicalExam(VisitTransactionBundleBuilder builder) {
		String valueJson = readTestDataLine("testdata/sample_Physical_Examination.json", 1);
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueJson);
		Observation source = sampleSourceObservation("Physical examination", "obs-pe-source");
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(valueJson);
		PhysicalExamObservationBuilder examBuilder = new PhysicalExamObservationBuilder();
		List<Observation> categoryObs = new ArrayList<>();
		for (ParsedExamCategory category : categories) {
			categoryObs.add(examBuilder.build(source, "obs-pe-source", category, sharedNote));
		}
		addClinicalResources(builder, new ArrayList<Condition>(), categoryObs, "obs-pe-source",
		    ProvenanceAssertionClass.PHYSICAL_EXAMINATION);
	}
	
	private void addFamilyHistory(VisitTransactionBundleBuilder builder) throws Exception {
		String valueJson = readTestDataLine("testdata/Sample_Family_History.json", 4);
		Observation source = sampleSourceObservation("Family history", "obs-fh-source");
		FamilyHistoryBuildResult built = new FamilyHistoryTransfer().build(source, "obs-fh-source", valueJson);
		List<Resource> resources = new ArrayList<>();
		for (FamilyMemberHistory history : built.getFamilyMemberHistories()) {
			resources.add(history);
		}
		addClinicalResources(builder, resources, "obs-fh-source", ProvenanceAssertionClass.FAMILY_HISTORY);
	}
	
	private void addMedicalHistory(VisitTransactionBundleBuilder builder) throws Exception {
		String valueJson = readTestDataLine("testdata/sample_Patient Medical History.json", 10);
		Observation source = sampleSourceObservation("Patient medical history", "obs-mh-source");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-mh-source", valueJson);
		List<Resource> resources = new ArrayList<>();
		resources.addAll(built.getObservations());
		resources.addAll(built.getAllergyIntolerances());
		resources.addAll(built.getMedicationStatements());
		resources.addAll(built.getConditions());
		addClinicalResources(builder, resources, "obs-mh-source", ProvenanceAssertionClass.PATIENT_MEDICAL_HISTORY);
	}
	
	private void addDiagnosis(VisitTransactionBundleBuilder builder) {
		Observation source = sampleSourceObservation("Diagnosis", "obs-dx-source");
		source.getEncounter().setReference("Encounter/" + ENC_CHILD);
		DiagnosisTransfer transfer = new DiagnosisTransfer();
		DiagnosisBuildResult primary = transfer.build(source, "dx-obs-primary",
		    "82272006::Acute rhinitis:Primary & Confirmed", 3);
		DiagnosisBuildResult secondary = transfer.build(source, "dx-obs-secondary",
		    "J30.0::Acute rhinitis:Secondary & Provisional", 3);
		
		List<Encounter.DiagnosisComponent> diagnosisComponents = new ArrayList<>();
		List<Resource> diagnosisResources = new ArrayList<>();
		for (DiagnosisBuildResult built : new DiagnosisBuildResult[] { primary, secondary }) {
			if (built == null || built.getCondition() == null) {
				continue;
			}
			Condition condition = built.getCondition();
			builder.addPutResource(condition, "[Diagnosis]");
			diagnosisResources.add(condition);
			String conditionId = VisitPushResourceIds.resolvePutResourceId(condition);
			Encounter.DiagnosisComponent dc = new Encounter.DiagnosisComponent();
			dc.setCondition(new Reference("Condition/" + conditionId));
			dc.setUse(new CodeableConcept().addCoding(new Coding().setSystem(DiagnosisConstants.DIAGNOSIS_ROLE_SYSTEM)
			        .setCode(DiagnosisConstants.DIAGNOSIS_ROLE_CODE)));
			dc.setRank(built.getRank());
			diagnosisComponents.add(dc);
		}
		Encounter encounter = sampleEncounter(ENC_CHILD, "Outpatient consult");
		encounter.setDiagnosis(diagnosisComponents);
		builder.addPutResource(encounter, "[Diagnosis]");
		diagnosisResources.add(encounter);
		for (Resource resource : diagnosisResources) {
			builder.registerProvenanceTarget(ProvenanceAssertionClass.DIAGNOSES, resource, "obs-dx-source");
		}
	}
	
	private void addOrders(VisitTransactionBundleBuilder builder) {
		ServiceRequest lab = new ServiceRequest();
		lab.setId("service-request-lab-001");
		lab.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
		lab.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
		lab.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		lab.getEncounter().setReference("Encounter/" + ENC_CHILD);
		lab.getCode().setText("Complete blood count");
		builder.addPutResource(lab, "[ServiceRequest]");
		builder.registerProvenanceTarget(ProvenanceAssertionClass.ORDERS_REFERRALS, lab, null);
		
		MedicationRequest rx = new MedicationRequest();
		rx.setId("medication-request-001");
		rx.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
		rx.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		rx.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		rx.getEncounter().setReference("Encounter/" + ENC_CHILD);
		rx.getMedicationCodeableConcept().setText("Paracetamol 500mg");
		builder.addPutResource(rx, "[MedicationRequest]");
		builder.registerProvenanceTarget(ProvenanceAssertionClass.PRESCRIPTIONS, rx, null);
	}
	
	private void addImage(VisitTransactionBundleBuilder builder) throws Exception {
		String json = new String(Files.readAllBytes(Paths.get("src/main/resources/testdata/image.json")),
		        StandardCharsets.UTF_8);
		JSONObject root = new JSONObject(json);
		String imageObsKey = "SELECT * from obs where concept_id =163371 or concept_id =163372\norder by obs_id desc limit 5";
		JSONArray arr = root.getJSONArray(imageObsKey);
		JSONObject row = arr.getJSONObject(0);
		String obsUuid = row.getString("uuid");
		String valueComplex = row.getString("value_complex");
		String comments = row.optString("comments");
		String fileName = valueComplex.substring(valueComplex.indexOf('|') + 1).trim();
		
		Path tempDir = Files.createTempDirectory("ihshr-visit-push-image");
		Files.write(tempDir.resolve(fileName), "sample-image-bytes".getBytes(StandardCharsets.UTF_8));
		System.setProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY, tempDir.toString());
		try {
			Observation source = sampleSourceObservation("Clinical image", obsUuid);
			boolean added = new ImageObsTransfer().appendToVisitBuilder(builder, source, obsUuid, valueComplex, comments);
			assertTrue(added);
		}
		finally {
			System.clearProperty(ImageObsConstants.COMPLEX_OBS_DIR_PROPERTY);
		}
	}
	
	private void addClinicalResources(VisitTransactionBundleBuilder builder, List<Condition> conditions,
	        List<Observation> observations, String sourceObsUuid, ProvenanceAssertionClass assertionClass) {
		List<Resource> resources = new ArrayList<>();
		if (conditions != null) {
			resources.addAll(conditions);
		}
		if (observations != null) {
			resources.addAll(observations);
		}
		addClinicalResources(builder, resources, sourceObsUuid, assertionClass);
	}
	
	private void addClinicalResources(VisitTransactionBundleBuilder builder, List<Resource> resources, String sourceObsUuid,
	        ProvenanceAssertionClass assertionClass) {
		for (Resource resource : resources) {
			builder.addPutResource(resource, "[test]");
			builder.registerProvenanceTarget(assertionClass, resource, sourceObsUuid);
		}
	}
	
	private static Encounter sampleEncounter(String encounterId, String typeDisplay) {
		Encounter encounter = new Encounter();
		encounter.setId(encounterId);
		encounter.setStatus(EncounterStatus.FINISHED);
		encounter.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		encounter.getClass_().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB")
		        .setDisplay(typeDisplay);
		encounter.setPeriod(new org.hl7.fhir.r4.model.Period().setStart(new Date()));
		return encounter;
	}
	
	private static Observation sampleSourceObservation(String codeText, String obsUuid) {
		Observation source = new Observation();
		source.setId(obsUuid);
		source.getSubject().setReference("Patient/" + OPENMRS_PATIENT_UUID);
		source.getEncounter().setReference("Encounter/" + ENC_CHILD);
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.addPerformer(new Reference("Practitioner/test-practitioner-uuid"));
		source.getCode().setText(codeText);
		return source;
	}
	
	private static String readTestDataLine(String resource, int lineNumber) {
		InputStream in = VisitPushPayloadTest.class.getClassLoader().getResourceAsStream(resource);
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
