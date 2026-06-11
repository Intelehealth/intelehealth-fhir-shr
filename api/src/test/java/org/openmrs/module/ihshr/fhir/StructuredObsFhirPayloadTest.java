package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.parser.ChiefComplaintParserTest;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;
import org.openmrs.module.ihshr.parser.MedicalHistoryTopicConfig;
import org.openmrs.module.ihshr.parser.PhysicalExamParser;
import org.openmrs.module.ihshr.parser.PhysicalExamParserTest;

import ca.uhn.fhir.context.FhirContext;

/**
 * Prints FHIR transaction-bundle JSON for all four structured obs types using testdata and
 * {@code /opt/intelehealth/shr-config/lookups/} when present. Run:
 * {@code mvn test -pl api -Dtest=StructuredObsFhirPayloadTest#printAllFourTypePayloads}
 */
public class StructuredObsFhirPayloadTest {
	
	private static final String LOOKUP_DIR = "/opt/intelehealth/shr-config";
	
	private static final String OBS_CC = "test-obs-163212";
	
	private static final String OBS_PE = "test-obs-163213";
	
	private static final String OBS_FH = "test-obs-163211";
	
	private static final String OBS_MH = "test-obs-163210";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void useOptLookupDir() {
		System.setProperty("ihshr.shr.lookup.dir", LOOKUP_DIR);
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
		logLookupFilesOnDisk();
	}
	
	@Test
	public void printAllFourTypePayloads() throws Exception {
		printChiefComplaintPayloads();
		printPhysicalExamPayloads();
		printFamilyHistoryPayloads();
		printMedicalHistoryPayloads();
	}
	
	private void printChiefComplaintPayloads() throws Exception {
		String valueJson = new JSONObject().put("en", ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC).toString();
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# 1. CHIEF COMPLAINT (concept 163212)");
		System.err.println("################################################################");
		System.err.println("[ChiefComplaint] JSON en length="
		        + ClinicalJsonValueTexts.extractClinicalHtml(valueJson).length());
		
		Observation source = sampleSourceObservation("Chief complaint");
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, OBS_CC, valueJson);
		System.err.println("[ChiefComplaint] Built conditions=" + built.getConditions().size() + " associatedObs="
		        + built.getAssociatedSymptomObservations().size());
		assertTrue(built.totalResourceCount() > 0);
		
		int idx = 0;
		for (Condition condition : built.getConditions()) {
			printPayload("ChiefComplaint Condition " + (++idx), condition);
		}
		idx = 0;
		for (Observation obs : built.getAssociatedSymptomObservations()) {
			printPayload("ChiefComplaint associated Observation " + (++idx), obs);
		}
	}
	
	private void printPhysicalExamPayloads() {
		String valueJson = readTestDataLine("testdata/sample_Physical_Examination.json", 1);
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# 2. PHYSICAL EXAMINATION (concept 163213)");
		System.err.println("################################################################");
		
		List<ParsedExamCategory> categories = new PhysicalExamParser().parse(valueJson);
		System.err.println("[PhysicalExam] Parsed categories=" + categories.size());
		assertTrue(!categories.isEmpty());
		
		String sharedNote = PhysicalExamObservationBuilder.stripHtmlForNote(valueJson);
		Observation source = sampleSourceObservation("Physical examination");
		PhysicalExamObservationBuilder builder = new PhysicalExamObservationBuilder();
		int index = 0;
		for (ParsedExamCategory category : categories) {
			Observation categoryObs = builder.build(source, OBS_PE, category, sharedNote);
			printPayload("PhysicalExam category " + (++index) + " (" + category.getCategoryName() + ")", categoryObs);
		}
	}
	
	private void printFamilyHistoryPayloads() throws Exception {
		String valueJson = readTestDataLine("testdata/Sample_Family_History.json", 4);
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# 3. FAMILY HISTORY (concept 163211)");
		System.err.println("################################################################");
		
		Observation source = sampleSourceObservation("Family history");
		FamilyHistoryBuildResult built = new FamilyHistoryTransfer().build(source, OBS_FH, valueJson);
		System.err.println("[FamilyHistory] Built FamilyMemberHistory count=" + built.getFamilyMemberHistories().size());
		assertTrue(built.totalResourceCount() > 0);
		
		int idx = 0;
		for (FamilyMemberHistory history : built.getFamilyMemberHistories()) {
			printPayload("FamilyHistory " + (++idx) + " (" + history.getRelationship().getText() + ")", history);
		}
	}
	
	private void printMedicalHistoryPayloads() throws Exception {
		String valueJson = readTestDataLine("testdata/sample_Patient Medical History.json", 10);
		System.err.println();
		System.err.println("################################################################");
		System.err.println("# 4. PATIENT MEDICAL HISTORY (concept 163210)");
		System.err.println("################################################################");
		
		Observation source = sampleSourceObservation("Patient medical history");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, OBS_MH, valueJson);
		System.err.println("[MedicalHistory] observations=" + built.getObservations().size() + " allergies="
		        + built.getAllergyIntolerances().size() + " medications=" + built.getMedicationStatements().size()
		        + " conditions=" + built.getConditions().size());
		assertTrue(built.totalResourceCount() > 0);
		
		int idx = 0;
		for (Observation obs : built.getObservations()) {
			printPayload("MedicalHistory Observation " + (++idx) + " (" + obs.getCode().getText() + ")", obs);
		}
		for (AllergyIntolerance allergy : built.getAllergyIntolerances()) {
			printPayload("MedicalHistory AllergyIntolerance", allergy);
		}
		for (MedicationStatement med : built.getMedicationStatements()) {
			printPayload("MedicalHistory MedicationStatement", med);
		}
		for (Condition condition : built.getConditions()) {
			printPayload("MedicalHistory Condition " + (++idx) + " (" + condition.getCode().getText() + ")", condition);
		}
	}
	
	private void logLookupFilesOnDisk() {
		File dir = new File(LOOKUP_DIR, ShrLookupLoader.LOOKUP_SUBDIR);
		System.err.println();
		System.err.println("========== Lookup directory: " + dir.getAbsolutePath() + " ==========");
		if (!dir.isDirectory()) {
			System.err.println("WARNING: directory not found; using classpath shr-config/lookups/");
			return;
		}
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.getName().endsWith(".json")) {
				System.err.println("  OK  " + file.getName() + " (" + file.length() + " bytes)");
			}
		}
		System.err.println("========== end lookup listing ==========");
		System.err.println();
	}
	
	private void printPayload(String label, Resource resource) {
		Bundle bundle = toTransactionBundle(resource);
		String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		System.err.println();
		System.err.println("---------- " + label + " PUT " + resource.fhirType() + "/" + resolvePutResourceId(resource)
		        + " ----------");
		System.err.println(payload);
		System.err.println("---------- end " + label + " ----------");
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
		if (resource instanceof FamilyMemberHistory) {
			return ((FamilyMemberHistory) resource).getIdentifierFirstRep().getValue();
		}
		if (resource instanceof AllergyIntolerance) {
			return ((AllergyIntolerance) resource).getIdentifierFirstRep().getValue();
		}
		if (resource instanceof MedicationStatement) {
			return ((MedicationStatement) resource).getIdentifierFirstRep().getValue();
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
		InputStream in = StructuredObsFhirPayloadTest.class.getClassLoader().getResourceAsStream(resource);
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
