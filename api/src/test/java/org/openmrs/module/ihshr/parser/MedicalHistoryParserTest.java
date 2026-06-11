package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedMedicalHistoryTopic;
import org.openmrs.module.ihshr.fhir.MedicalHistoryBuildResult;
import org.openmrs.module.ihshr.fhir.MedicalHistoryTransfer;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;

public class MedicalHistoryParserTest {
	
	@Before
	public void loadLookups() {
		System.setProperty("ihshr.shr.lookup.dir", "src/main/resources/shr-config");
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
	}
	
	/** Plain HTML with {@code ?} markers (legacy / alternate client format). */
	public static final String PLAIN_QUESTION_MARK_HTML = "? Current Vaccinations status - Complete.<br/>"
	        + "? Pregnancy status - Pregnancy status not known.<br/>" + "? Medical History - df.<br/>"
	        + "? Drug history - No recent medication.<br/>" + "? Allergies - No known allergies.<br/>"
	        + "? Chewing tobacco status - Do not Chew/denied answer.<br/>"
	        + "? Smoking history - Patient denied/has no h/o smoking.<br/>" + "? Alcohol use - No/Denied.<br/>";
	
	@Test
	public void parse_plainQuestionMarkHtml_parsesEightTopics() {
		List<ParsedMedicalHistoryTopic> topics = new MedicalHistoryParser().parse(PLAIN_QUESTION_MARK_HTML);
		assertEquals(8, topics.size());
		ParsedMedicalHistoryTopic medical = findTopic(topics, "medical history");
		assertEquals("df", medical.getValue());
	}
	
	@Test
	public void matchesValueText_plainQuestionMarkHtml() {
		assertTrue(org.openmrs.module.ihshr.utils.MedicalHistoryMatcher.matchesValueText(PLAIN_QUESTION_MARK_HTML));
	}
	
	@Test
	public void build_plainQuestionMarkHtml_suppressesNegatives_emitsMedicalHistoryCondition() {
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-163210", PLAIN_QUESTION_MARK_HTML);
		assertEquals(1, built.getConditions().size());
		assertEquals("df", built.getConditions().get(0).getCode().getText());
		assertTrue(built.getAllergyIntolerances().isEmpty());
		assertTrue(built.getMedicationStatements().isEmpty());
		assertTrue(built.getObservations().size() >= 1);
		Observation vaccination = built.getObservations().stream()
		        .filter(obs -> "Current vaccination status".equals(obs.getCode().getText())).findFirst().orElse(null);
		assertEquals("Complete", ((CodeableConcept) vaccination.getValue()).getText());
	}
	
	@Test
	public void buildTopicNoteText_formatsSinglePositiveLineWithoutBullet() {
		ParsedMedicalHistoryTopic topic = new ParsedMedicalHistoryTopic("smoking history", "Smoking history",
		        "Patient is a smoker - 10 Years");
		assertEquals("Smoking history - Patient is a smoker - 10 Years.", MedicalHistoryParser.buildTopicNoteText(topic));
	}
	
	@Test
	public void build_twoPositiveTopics_eachResourceGetsOwnNoteOnly() {
		String json = "{\"en\":\"• Current Vaccinations status - Complete.<br/>"
		        + "• Medical History - Cancer/Tumour - Current medication - Hahhshau.<br/>"
		        + "• Drug history - No recent medication.<br/>"
		        + "• Allergies - No known allergies.<br/>"
		        + "• Chewing tobacco status - Denied answer.<br/>"
		        + "• Smoking history* - Patient is a smoker - Since -  5 Months. How many cigarettes per day do you smoke? - 5.<br/>"
		        + "• Alcohol use* - Denied.<br/>\"}";
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-163210", json);
		assertEquals(2, built.getObservations().size());
		assertEquals(1, built.getConditions().size());
		Observation vaccination = built.getObservations().stream()
		        .filter(obs -> "Current vaccination status".equals(obs.getCode().getText())).findFirst().orElse(null);
		assertEquals("Current Vaccinations status - Complete.", vaccination.getNoteFirstRep().getText());
		Observation smoking = built.getObservations().stream()
		        .filter(obs -> "Smoking history*".equals(obs.getCode().getText())).findFirst().orElse(null);
		assertEquals(
		        "Smoking history* - Patient is a smoker - Since -  5 Months. How many cigarettes per day do you smoke? - 5.",
		        smoking.getNoteFirstRep().getText());
		assertEquals("Medical History - Cancer/Tumour - Current medication - Hahhshau.",
		        built.getConditions().get(0).getNoteFirstRep().getText());
	}
	
	@Test
	public void build_vaccinationComplete_emitsObservationWithCodeableValue() {
		String json = "{\"en\":\"• Current Vaccinations status - Complete.<br/>" + "• Medical History - None.<br/>"
		        + "• Drug history - No recent medication.<br/>" + "• Allergies - No known allergies.<br/>"
		        + "• Chewing tobacco status - Do not Chew.<br/>"
		        + "• Smoking history* - Patient denied/has no h/o smoking.<br/>" + "• Alcohol use* - No.<br/>\"}";
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-163210", json);
		assertEquals(1, built.getObservations().size());
		Observation vaccination = built.getObservations().get(0);
		assertEquals("Current vaccination status", vaccination.getCode().getText());
		assertTrue(vaccination.getValue() instanceof CodeableConcept);
		assertEquals("Complete", ((CodeableConcept) vaccination.getValue()).getText());
		assertEquals("Patient/test-patient", vaccination.getSubject().getReference());
	}
	
	private static ParsedMedicalHistoryTopic findTopic(List<ParsedMedicalHistoryTopic> topics, String key) {
		for (ParsedMedicalHistoryTopic topic : topics) {
			if (key.equals(topic.getTopicKey())) {
				return topic;
			}
		}
		return null;
	}
	
	@Test
	public void parse_smokingPositiveTopic() throws Exception {
		String raw = readTestDataLine("testdata/sample_Patient Medical History.json", 10);
		List<ParsedMedicalHistoryTopic> topics = new MedicalHistoryParser().parse(raw);
		assertTrue(topics.size() >= 5);
		boolean hasSmoker = false;
		for (ParsedMedicalHistoryTopic topic : topics) {
			if ("smoking history".equals(topic.getTopicKey()) && topic.getValue().toLowerCase().contains("smoker")) {
				hasSmoker = true;
			}
		}
		assertTrue(hasSmoker);
	}
	
	@Test
	public void negativeFilter_suppressesNoAnswers() throws Exception {
		String raw = readTestDataLine("testdata/sample_Patient Medical History.json", 1);
		List<ParsedMedicalHistoryTopic> topics = new MedicalHistoryParser().parse(raw);
		MedicalHistoryTopicConfig config = MedicalHistoryTopicConfig.getInstance();
		int positive = 0;
		for (ParsedMedicalHistoryTopic topic : topics) {
			if (!MedicalHistoryNegativeFilter.isNegative(topic.getValue(), config.getNegativePatterns())) {
				positive++;
			}
		}
		assertEquals(1, positive);
	}
	
	@Test
	public void splitMedicalHistoryConditions_splitsCommaList() {
		List<String> conditions = MedicalHistoryParser.splitMedicalHistoryConditions("Diabetes, Hypertension");
		assertEquals(2, conditions.size());
		assertEquals("Diabetes", conditions.get(0));
		assertEquals("Hypertension", conditions.get(1));
	}
	
	@Test
	public void splitMedicalHistoryConditions_noneIsEmpty() {
		assertTrue(MedicalHistoryParser.splitMedicalHistoryConditions("None.").isEmpty());
	}
	
	@Test
	public void build_cancerAndSmoker_emitsConditionAndObservation() throws Exception {
		String raw = readTestDataLine("testdata/sample_Patient Medical History.json", 10);
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-163210", raw);
		assertFalse(built.getConditions().isEmpty());
		assertFalse(built.getObservations().isEmpty());
	}
	
	@Test
	public void build_allergyDust_emitsAllergyIntolerance() throws Exception {
		String raw = readTestDataLine("testdata/sample_Patient Medical History.json", 18);
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient");
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, "obs-163210", raw);
		assertEquals(1, built.getAllergyIntolerances().size());
		assertEquals("Dust", built.getAllergyIntolerances().get(0).getCode().getText());
	}
	
	private static String readTestDataLine(String resource, int lineNumber) throws Exception {
		InputStream in = MedicalHistoryParserTest.class.getClassLoader().getResourceAsStream(resource);
		if (in == null) {
			throw new IllegalStateException("Missing test resource: " + resource);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return reader.lines().skip(lineNumber - 1).findFirst().orElseThrow(IllegalStateException::new);
		}
	}
}
