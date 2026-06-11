package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedComplaintBundle;
import org.openmrs.module.ihshr.fhir.ChiefComplaintBuildResult;
import org.openmrs.module.ihshr.fhir.ChiefComplaintTransfer;

import ca.uhn.fhir.context.FhirContext;

public class ChiefComplaintCoughSampleTest {
	
	public static final String COUGH_JSON = "{\"en\":\"►<b>Cough</b>: <br/>• Aggravating factors - Cold weather.<br/>• Type of cough - Dry.<br/>• Recent h/o medication - None.<br/>• Smoking - No h/o of smoking.<br/>• Occupational history - Declined to answer.<br/>• Prior treatment sought - No.<br/>►<b> Associated symptoms</b>:  <br/>• Patient denies -<br/> Burning feeling in a throat at night/early in the morning,  Fever,  Hemoptysis,  Hoarseness,  Nasal congestion/Stuffy nose,  Pain/Tightness of chest,  Post nasal drip,  Recent severe stress,  Recurrent diarrhoea,  Runny nose,  Shortness of breath,  Wheezing<br/> \",\"l-en\":\"►Cough::● What aggravates the cough?<br/>•Cold weather<br/>● Is the cough dry or wet?<br/>•Dry<br/>● Have you taken any medication recently?<br/>•None<br/>● Do you smoke/have ever smoked?<br/>•No<br/>● What is your occupation?<br/>•Declined to answer<br/>● Have you taken any treatment (including self-medication or home remedies) or seen any health provider for this problem before coming here today?<br/>•No<br/>►Do you have the following symptom(s)?::Patient denies -<br/>•Burning feeling in a throat at night/early in the morning<br/>•Fever<br/>•Bloody sputum<br/>•Hoarseness<br/>•Nasal congestion/Stuffy nose<br/>•Other [describe]<br/>•Pain/Tightness of chest<br/>•Does mucous drip in the back of your throat?<br/>•Recent severe stress<br/>•Recurrent diarrhoea<br/>•Runny nose<br/>•Shortness of breath<br/>•Weight change (kg)<br/>•Wheezing<br/>\"}";
	
	private static final String OBS_UUID = "sample-obs-163212-cough";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFhirOutput() throws Exception {
		Date encounterDate = parseDate("2025-06-03T14:30:00+05:30");
		
		ParsedComplaintBundle parsed = new ChiefComplaintParser().parse(COUGH_JSON, encounterDate);
		System.err.println("=== PARSE SUMMARY ===");
		System.err.println("complaints=" + parsed.getComplaints().size());
		for (int i = 0; i < parsed.getComplaints().size(); i++) {
			System.err.println("  [" + i + "] symptom=" + parsed.getComplaints().get(i).getSymptom());
			System.err.println("  [" + i + "] duration=" + parsed.getComplaints().get(i).getDurationText());
		}
		if (parsed.getAssociatedSymptoms() != null) {
			System.err.println("reports=" + parsed.getAssociatedSymptoms().getReports());
			System.err.println("denies=" + parsed.getAssociatedSymptoms().getDenies());
			System.err.println("reportCount=" + parsed.getAssociatedSymptoms().getReports().size());
			System.err.println("denyCount=" + parsed.getAssociatedSymptoms().getDenies().size());
		}
		
		Observation source = new Observation();
		source.getSubject().setReference("Patient/example-patient");
		source.getEncounter().setReference("Encounter/example-encounter");
		source.setEffective(new DateTimeType(encounterDate));
		
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, OBS_UUID, COUGH_JSON);
		System.err.println();
		System.err.println("=== FHIR BUILD SUMMARY ===");
		System.err.println("conditions=" + built.getConditions().size());
		System.err.println("associatedObs=" + built.getAssociatedSymptomObservations().size());
		System.err.println("total=" + built.totalResourceCount());
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.COLLECTION);
		for (Condition c : built.getConditions()) {
			bundle.addEntry().setResource(c);
		}
		for (Observation o : built.getAssociatedSymptomObservations()) {
			bundle.addEntry().setResource(o);
		}
		
		String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		Path out = Paths.get("target", "chief_complaint_cough_sample_bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("=== FHIR JSON ===");
		System.err.println(json);
		assertTrue(built.totalResourceCount() > 0);
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
}
