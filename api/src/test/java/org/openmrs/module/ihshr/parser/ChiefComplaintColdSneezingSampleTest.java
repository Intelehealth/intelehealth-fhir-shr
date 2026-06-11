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

/**
 * Sample output for plain-HTML Cold/Sneezing chief complaint. Run:
 * {@code mvn test -pl api -Dtest=ChiefComplaintColdSneezingSampleTest#printFhirOutput}
 */
public class ChiefComplaintColdSneezingSampleTest {
	
	private static final String OBS_UUID = "sample-obs-163212-cold-sneezing";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFhirOutput() throws Exception {
		String valueText = ChiefComplaintParserTest.COLD_SNEEZING_WITH_ASSOC;
		Date encounterDate = parseDate("2025-06-03T14:30:00+05:30");
		
		ParsedComplaintBundle parsed = new ChiefComplaintParser().parse(valueText, encounterDate);
		System.err.println("=== PARSE SUMMARY ===");
		System.err.println("complaints=" + parsed.getComplaints().size());
		if (!parsed.getComplaints().isEmpty()) {
			System.err.println("symptom=" + parsed.getComplaints().get(0).getSymptom());
			System.err.println("duration=" + parsed.getComplaints().get(0).getDurationText());
			System.err.println("onset=" + parsed.getComplaints().get(0).getOnsetDateTime());
		}
		if (parsed.getAssociatedSymptoms() != null) {
			System.err.println("reports=" + parsed.getAssociatedSymptoms().getReports());
			System.err.println("denies=" + parsed.getAssociatedSymptoms().getDenies());
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
		Path out = Paths.get("target", "chief_complaint_cold_sneezing_sample_bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("=== FHIR JSON (Collection bundle) ===");
		System.err.println(json);
		assertTrue(built.totalResourceCount() > 0);
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
}
