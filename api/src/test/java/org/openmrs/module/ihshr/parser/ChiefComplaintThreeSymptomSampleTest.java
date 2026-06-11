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

public class ChiefComplaintThreeSymptomSampleTest {
	
	private static final String OBS_UUID = "sample-obs-163212-three-symptoms";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFhirOutput() throws Exception {
		String valueText = ChiefComplaintParserTest.THREE_SYMPTOM_INPUT;
		Date encounterDate = parseDate("2025-04-30T11:30:00+05:30");
		
		ParsedComplaintBundle parsed = new ChiefComplaintParser().parse(valueText, encounterDate);
		System.err.println("=== PARSE SUMMARY ===");
		System.err.println("complaints=" + parsed.getComplaints().size());
		for (int i = 0; i < parsed.getComplaints().size(); i++) {
			System.err.println("  [" + i + "] symptom=" + parsed.getComplaints().get(i).getSymptom());
			System.err.println("  [" + i + "] duration=" + parsed.getComplaints().get(i).getDurationText());
			System.err.println("  [" + i + "] onset=" + parsed.getComplaints().get(i).getOnsetDateTime());
		}
		
		Observation source = new Observation();
		source.getSubject().setReference("Patient/example-patient");
		source.getEncounter().setReference("Encounter/example-encounter");
		source.setEffective(new DateTimeType(encounterDate));
		
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, OBS_UUID, valueText);
		System.err.println();
		System.err.println("=== FHIR BUILD SUMMARY ===");
		System.err.println("conditions=" + built.getConditions().size());
		System.err.println("associatedObs=" + built.getAssociatedSymptomObservations().size());
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.COLLECTION);
		for (Condition c : built.getConditions()) {
			bundle.addEntry().setResource(c);
		}
		for (Observation o : built.getAssociatedSymptomObservations()) {
			bundle.addEntry().setResource(o);
		}
		
		String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		Path out = Paths.get("target", "chief_complaint_three_symptom_sample_bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("=== FHIR JSON ===");
		System.err.println(json);
		assertTrue(built.getConditions().size() == 3);
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
}
