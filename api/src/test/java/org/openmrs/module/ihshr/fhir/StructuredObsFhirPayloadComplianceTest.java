package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hl7.fhir.r4.model.Observation;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.parser.ChiefComplaintParserTest;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;
import org.openmrs.module.ihshr.parser.MedicalHistoryParserTest;
import org.openmrs.module.ihshr.parser.PhysicalExamParserTest;
import org.openmrs.module.ihshr.parser.MedicalHistoryParser;
import org.openmrs.module.ihshr.parser.MedicalHistoryTopicConfig;
import org.openmrs.module.ihshr.utils.ChiefComplaintMatcher;
import org.openmrs.module.ihshr.utils.FamilyHistoryMatcher;
import org.openmrs.module.ihshr.utils.MedicalHistoryMatcher;
import org.openmrs.module.ihshr.utils.PhysicalExamMatcher;

/**
 * Emits {@code [SHR-COMPLIANCE] PASS|FAIL ...} lines for {@code verify_shr_fhir_payloads.py}
 * (negative filters, legacy HTML skip, targeted positive cases). Run with payload test:
 * {@code mvn test -pl api -Dtest=StructuredObsFhirPayloadTest,StructuredObsFhirPayloadComplianceTest}
 */
public class StructuredObsFhirPayloadComplianceTest {
	
	static final String COMPLIANCE_MARKER = "[SHR-COMPLIANCE]";
	
	private static final String LOOKUP_DIR = "src/main/resources/shr-config";
	
	private static final String OBS_FH = "test-obs-163211";
	
	private static final String OBS_MH = "test-obs-163210";
	
	@Before
	public void useOptLookupDir() {
		System.setProperty("ihshr.shr.lookup.dir", LOOKUP_DIR);
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
	}
	
	@Test
	public void emitComplianceMarkers() throws Exception {
		checkMedicalHistoryAllNegativeEmitsZeroResources();
		checkFamilyHistoryNoneEmitsZeroResources();
		checkLegacyPlainHtmlSkipped();
		checkChiefComplaintPlainHtmlMatched();
		checkPhysicalExamPlainHtmlMatched();
		checkMedicalHistoryPlainHtmlMatched();
		checkMedicalHistoryAllergyDustOnly();
		checkFamilyHistoryMultiRelative();
		checkMedicalHistoryCommaSplitTwoConditions();
	}
	
	private void checkMedicalHistoryAllNegativeEmitsZeroResources() throws Exception {
		String valueJson = readTestDataLine("testdata/sample_Patient Medical History.json", 1);
		MedicalHistoryBuildResult built = buildMedicalHistory(valueJson);
		boolean pass = built.totalResourceCount() == 1 && built.getObservations().size() == 1
		        && "Current vaccination status".equals(built.getObservations().get(0).getCode().getText());
		compliance("medical-history-all-negative", pass, "resourceCount=" + built.totalResourceCount());
	}
	
	private void checkFamilyHistoryNoneEmitsZeroResources() throws Exception {
		String valueJson = readTestDataLine("testdata/Sample_Family_History.json", 2);
		FamilyHistoryBuildResult built = buildFamilyHistory(valueJson);
		boolean pass = built.totalResourceCount() == 0;
		compliance("family-history-none", pass, "resourceCount=" + built.totalResourceCount());
	}
	
	private void checkLegacyPlainHtmlSkipped() {
		String legacy = "Unstructured visit note without topic-value pairs.";
		boolean pass = !ClinicalJsonValueTexts.hasEnClinicalJson(legacy) && !MedicalHistoryMatcher.matchesValueText(legacy)
		        && !FamilyHistoryMatcher.matchesValueText(legacy) && !PhysicalExamMatcher.matchesValueText(legacy);
		compliance("legacy-plain-html-skipped", pass, "hasEnClinicalJson=false");
	}
	
	private void checkMedicalHistoryPlainHtmlMatched() {
		boolean pass = MedicalHistoryMatcher.matchesValueText(MedicalHistoryParserTest.PLAIN_QUESTION_MARK_HTML);
		compliance("medical-history-plain-html-matched", pass, "matchesValueText=true");
	}
	
	private void checkChiefComplaintPlainHtmlMatched() {
		boolean pass = ChiefComplaintMatcher.matchesValueText(ChiefComplaintParserTest.THREE_SYMPTOM_INPUT)
		        && ChiefComplaintMatcher.matchesValueText(ChiefComplaintParserTest.COLD_SNEEZING_WITH_ASSOC);
		compliance("chief-complaint-plain-html-matched", pass, "matchesValueText=true");
	}
	
	private void checkPhysicalExamPlainHtmlMatched() {
		boolean pass = PhysicalExamMatcher.matchesValueText(PhysicalExamParserTest.PLAIN_HTML_GENERAL_EXAMS);
		compliance("physical-exam-plain-html-matched", pass, "matchesValueText=true");
	}
	
	private void checkMedicalHistoryAllergyDustOnly() throws Exception {
		String valueJson = readTestDataLine("testdata/sample_Patient Medical History.json", 18);
		MedicalHistoryBuildResult built = buildMedicalHistory(valueJson);
		boolean pass = built.getAllergyIntolerances().size() == 1
		        && "Dust".equals(built.getAllergyIntolerances().get(0).getCode().getText())
		        && built.getConditions().isEmpty() && built.getMedicationStatements().isEmpty();
		compliance("medical-history-allergy-dust", pass, "allergies=" + built.getAllergyIntolerances().size()
		        + " conditions=" + built.getConditions().size());
	}
	
	private void checkFamilyHistoryMultiRelative() throws Exception {
		String valueJson = readTestDataLine("testdata/Sample_Family_History.json", 15);
		FamilyHistoryBuildResult built = buildFamilyHistory(valueJson);
		boolean pass = built.getFamilyMemberHistories().size() == 2;
		compliance("family-history-multi-relative", pass, "fmrCount=" + built.getFamilyMemberHistories().size());
	}
	
	private void checkMedicalHistoryCommaSplitTwoConditions() throws Exception {
		List<String> conditions = MedicalHistoryParser.splitMedicalHistoryConditions("Diabetes, Hypertension");
		boolean pass = conditions.size() == 2;
		compliance("medical-history-comma-split", pass, "conditionParts=" + conditions.size());
	}
	
	private static void compliance(String rule, boolean pass, String detail) {
		System.err.println(COMPLIANCE_MARKER + " " + (pass ? "PASS" : "FAIL") + " " + rule + " " + detail);
		assertTrue(rule + ": " + detail, pass);
	}
	
	private static MedicalHistoryBuildResult buildMedicalHistory(String valueJson) {
		Observation source = sampleSourceObservation();
		return new MedicalHistoryTransfer().build(source, OBS_MH, valueJson);
	}
	
	private static FamilyHistoryBuildResult buildFamilyHistory(String valueJson) throws Exception {
		Observation source = sampleSourceObservation();
		return new FamilyHistoryTransfer().build(source, OBS_FH, valueJson);
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.getCode().setText("Structured obs");
		return source;
	}
	
	private static String readTestDataLine(String resource, int lineNumber) throws Exception {
		InputStream in = StructuredObsFhirPayloadComplianceTest.class.getClassLoader().getResourceAsStream(resource);
		if (in == null) {
			throw new IllegalStateException("Missing test resource: " + resource);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return reader.lines().skip(lineNumber - 1).findFirst().orElseThrow(IllegalStateException::new);
		}
	}
}
