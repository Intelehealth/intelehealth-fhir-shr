package org.openmrs.module.ihshr.parser;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedMedicalHistoryTopic;
import org.openmrs.module.ihshr.fhir.MedicalHistoryBuildResult;
import org.openmrs.module.ihshr.fhir.MedicalHistoryTransfer;

import ca.uhn.fhir.context.FhirContext;

public class MedicalHistoryFinalOutputWriterTest {
	
	public static final String USER_JSON_VACCINATIONS_HBP = "{\"en\":\"• Current Vaccinations status - Complete.<br/>"
	        + "• Medical History - High Blood Pressure - Current medication - Not taking any medication.<br/>"
	        + "• Drug history - No recent medication.<br/>" + "• Allergies - No known allergies.<br/>"
	        + "• Chewing tobacco status - Do not Chew.<br/>"
	        + "• Smoking history* - Patient denied/has no h/o smoking.<br/>" + "• Alcohol use* - No.<br/>\","
	        + "\"l-en\":\"● Has your child been vaccinated?<br/>" + "•Complete<br/>"
	        + "● Do you have a history of any of the following?*<br/>" + "•  High Blood Pressure<br/>"
	        + "▻ Current medication<br/>" + "▻Not taking any medication<br/>"
	        + "● Have you recently taken any kind of medicine (including ayurvedic/homeopathic/unani/herbal)?*<br/>"
	        + "•No<br/>" + "● Do you have any allergies?*<br/>" + "•No known allergies<br/>" + "● Do you chew tobbaco?<br/>"
	        + "•Do not Chew<br/>" + "● Smoking history*<br/>" + "•Never-smoker<br/>" + "● Alcohol use*<br/>" + "•No<br/>\"}";
	
	public static final String USER_JSON_MENSTRUAL_COMORBIDITIES = "{\"en\":\"• Menstrual History* - Status - Patient is pre-menopausal.<br/>"
	        + "• Known comorbidities - Diabetes - Since - 4 years. Current medication - No. Status - Testing.<br/>"
	        + "• Current medications - No recent medication.<br/>"
	        + "• Allergies - No known allergies.<br/>\","
	        + "\"l-en\":\"● Menstrual History*<br/>"
	        + "•  Status<br/>"
	        + "▻Pre-menopausal<br/>"
	        + "● Comorbidities*<br/>"
	        + "•  Diabetes<br/>"
	        + "▻ Since<br/>"
	        + "▻4 years<br/>"
	        + "▻ Current medication<br/>"
	        + "▻No<br/>"
	        + "▻ Status<br/>"
	        + "▻Testing<br/>"
	        + "● Current medications<br/>"
	        + "•No<br/>"
	        + "● Do you have any allergies?*<br/>" + "•No known allergies<br/>\"}";
	
	public static final String USER_JSON = "{\"en\":\"• Medical History - None.<br/>"
	        + "• Drug history - No recent medication.<br/>" + "• Allergies - No known allergies.<br/>"
	        + "• Chewing tobacco status - Denied answer.<br/>"
	        + "• Smoking history* - Patient denied/has no h/o smoking.<br/>" + "• Alcohol use* - No.<br/>\","
	        + "\"l-en\":\"● Do you have a history of any of the following?*<br/>" + "•None<br/>\"}";
	
	private static final String OBS_UUID = "920352ce-4e64-4b55-9dfe-883332b546c0";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Before
	public void loadLookups() {
		System.setProperty("ihshr.shr.lookup.dir", "src/main/resources/shr-config");
		ShrLookupLoader.clearCache();
		MedicalHistoryTopicConfig.reload();
	}
	
	@Test
	public void writeAllNegativeOutputFile() throws Exception {
		writeOutput(USER_JSON, "medical_history_all_negative");
	}
	
	@Test
	public void writeVaccinationsHbpOutputFile() throws Exception {
		writeOutput(USER_JSON_VACCINATIONS_HBP, "medical_history_vaccinations_hbp");
	}
	
	@Test
	public void writeMenstrualComorbiditiesOutputFile() throws Exception {
		writeOutput(USER_JSON_MENSTRUAL_COMORBIDITIES, "medical_history_menstrual_comorbidities");
	}
	
	private void writeOutput(String userJson, String basename) throws Exception {
		MedicalHistoryParser parser = new MedicalHistoryParser();
		List<ParsedMedicalHistoryTopic> allTopics = parser.parse(userJson);
		MedicalHistoryTopicConfig config = MedicalHistoryTopicConfig.getInstance();
		
		JSONArray parsedTopics = new JSONArray();
		JSONArray positiveTopics = new JSONArray();
		List<ParsedMedicalHistoryTopic> positive = new java.util.ArrayList<ParsedMedicalHistoryTopic>();
		for (ParsedMedicalHistoryTopic topic : allTopics) {
			JSONObject o = new JSONObject();
			o.put("topicKey", topic.getTopicKey());
			o.put("topicLabel", topic.getTopicLabel());
			o.put("value", topic.getValue());
			boolean negative = MedicalHistoryNegativeFilter.isNegative(topic.getValue(), config.getNegativePatterns());
			o.put("negative", negative);
			parsedTopics.put(o);
			if (!negative) {
				positiveTopics.put(o);
				positive.add(topic);
			}
		}
		
		Observation source = sampleSourceObservation();
		MedicalHistoryBuildResult built = new MedicalHistoryTransfer().build(source, OBS_UUID, userJson);
		
		JSONObject root = new JSONObject();
		root.put("source", "medical_history_final_output");
		root.put("basename", basename);
		root.put("obsUuid", OBS_UUID);
		root.put("parsedFrom", "en");
		root.put("lEnIgnored", true);
		root.put("input", new JSONObject(userJson));
		root.put("parsedTopics", parsedTopics);
		root.put("positiveTopics", positiveTopics);
		root.put("sharedNote", MedicalHistoryParser.buildPositiveNoteText(positive));
		JSONObject counts = new JSONObject();
		counts.put("observations", built.getObservations().size());
		counts.put("allergyIntolerances", built.getAllergyIntolerances().size());
		counts.put("medicationStatements", built.getMedicationStatements().size());
		counts.put("conditions", built.getConditions().size());
		counts.put("total", built.totalResourceCount());
		root.put("resourceCounts", counts);
		
		Bundle bundle = toTransactionBundle(built);
		String bundleJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		root.put("fhirTransactionBundle", new JSONObject(bundleJson));
		
		write(new File("target/" + basename + "_output.json"), root);
		write(new File("target/" + basename + "_fhir_output.json"), new JSONObject(bundleJson));
		write(new File("src/main/resources/testdata/" + basename + "_output.json"), root);
		write(new File("src/main/resources/testdata/" + basename + "_fhir_output.json"), new JSONObject(bundleJson));
		System.out.println(basename + " totalResources=" + built.totalResourceCount());
	}
	
	private static Bundle toTransactionBundle(MedicalHistoryBuildResult built) {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		for (Observation obs : built.getObservations()) {
			addEntry(bundle, obs, "Observation", obs.getIdentifier().get(0).getValue());
		}
		for (AllergyIntolerance allergy : built.getAllergyIntolerances()) {
			addEntry(bundle, allergy, "AllergyIntolerance", allergy.getIdentifier().get(0).getValue());
		}
		for (MedicationStatement med : built.getMedicationStatements()) {
			addEntry(bundle, med, "MedicationStatement", med.getIdentifier().get(0).getValue());
		}
		for (Condition condition : built.getConditions()) {
			addEntry(bundle, condition, "Condition", condition.getIdentifier().get(0).getValue());
		}
		return bundle;
	}
	
	private static void addEntry(Bundle bundle, org.hl7.fhir.r4.model.Resource resource, String type, String id) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setResource(resource);
		entry.getRequest().setUrl(type + "/" + id).setMethod(Bundle.HTTPVerb.PUT);
	}
	
	private static void write(File file, JSONObject json) throws Exception {
		file.getParentFile().mkdirs();
		try (Writer w = new FileWriter(file)) {
			w.write(json.toString(2));
		}
		System.out.println("Wrote: " + file.getAbsolutePath());
	}
	
	private static Observation sampleSourceObservation() {
		Observation source = new Observation();
		source.getSubject().setReference("Patient/test-patient-uuid");
		source.getEncounter().setReference("Encounter/test-encounter-uuid");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		source.getCode().setText("Patient medical history");
		return source;
	}
}
