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
 * One-off sample: user abdominal pain + associated symptoms JSON (concept 163212 shape). Run: mvn
 * test -pl api -Dtest=ChiefComplaintAbdominalUmbilicalSampleTest#printFhirOutput
 */
public class ChiefComplaintAbdominalUmbilicalSampleTest {
	
	public static final String USER_VALUE_JSON = "{\"en\":\"►<b>Abdominal Pain</b>: <br/>• Site - Middle (C) - Umbilical.<br/>• Pain does not radiate.<br/>• Duration -  10 Hours.<br/>• Onset - Gradual.<br/>• Timing - Morning.<br/>• Character of the pain* - Constant.<br/>• Severity - Mild, 1-3.<br/>• Exacerbating Factors - Hunger.<br/>• Relieving Factors - None.<br/>• Prior treatment sought - None.<br/>►<b> Associated symptoms</b>:  <br/>• Patient reports -<br/> Abdominal distention/Bloating,  Breathlessness,  Diarrhea,  Nausea,  Vomiting <br/>• Patient denies -<br/> Anorexia,  Belching/Burping,  Blood in stool,  Change in appetite,  change in frequency of urination,  Color change in stool [describe],  Color change in urine,  Constipation,  Fever,  Hiccups,  Injury,  Passing gas,  Restlessness<br/> \",\"l-en\":\"►Abdominal Pain::● Which part of the abdomen do you feel pain?*<br/>•Middle (C) - Umbilical<br/>● Does the pain move to other parts of the body?*<br/>•Does not move<br/>● Since when have you had this symptom?*<br/>• 10 Hours<br/>● How did the pain start?<br/>•Gradual<br/>● What time of the day do you feel the pain?<br/>•Morning<br/>● Character of the pain*<br/>•Constant<br/>● How severe is the pain?*<br/>•Mild, 1-3<br/>● What worsens the pain?*<br/>•Hunger<br/>● What relieves/lessens the pain?*<br/>•None<br/>● Have you taken any treatment (including self-medication or home remedies) or seen any health provider for this problem before coming here today?*<br/>•None<br/>►Do you have the following symptom(s)?*::•Abdominal distention/Bloating<br/>•Breathlessness<br/>•Diarrhea<br/>•Nausea<br/>•Vomiting<br/>Patient denies -<br/>•Anorexia<br/>•Belching/Burping<br/>•Blood in stool<br/>•Change in appetite<br/>•Change in frequency of urination [describe]<br/>•Color change in stool [describe]<br/>•Color change in urine [describe]<br/>•Constipation<br/>•Fever<br/>•Hiccups<br/>•Injury<br/>•Other [describe]<br/>•Passing gas<br/>•Restlessness<br/>\"}";
	
	private static final String OBS_UUID = "sample-obs-163212-umbilical";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFhirOutput() throws Exception {
		Date encounterDate = parseDate("2025-06-03T14:30:00+05:30");
		
		ParsedComplaintBundle parsed = new ChiefComplaintParser().parse(USER_VALUE_JSON, encounterDate);
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
			System.err.println("reportCount=" + parsed.getAssociatedSymptoms().getReports().size());
			System.err.println("denyCount=" + parsed.getAssociatedSymptoms().getDenies().size());
		}
		
		Observation source = new Observation();
		source.getSubject().setReference("Patient/example-patient");
		source.getEncounter().setReference("Encounter/example-encounter");
		source.setEffective(new DateTimeType(encounterDate));
		
		ChiefComplaintBuildResult built = new ChiefComplaintTransfer().build(source, OBS_UUID, USER_VALUE_JSON);
		System.err.println();
		System.err.println("=== FHIR BUILD SUMMARY ===");
		System.err.println("conditions=" + built.getConditions().size());
		System.err.println("associatedObs=" + built.getAssociatedSymptomObservations().size());
		System.err.println("orphanAssociated=" + built.isOrphanAssociatedSymptoms());
		assertTrue(built.totalResourceCount() > 0);
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.COLLECTION);
		for (Condition c : built.getConditions()) {
			bundle.addEntry().setResource(c);
		}
		for (Observation o : built.getAssociatedSymptomObservations()) {
			bundle.addEntry().setResource(o);
		}
		
		String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		Path out = Paths.get("target", "chief_complaint_umbilical_sample_bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
		System.err.println();
		System.err.println("=== FHIR JSON (Collection bundle) ===");
		System.err.println("Written to: " + out.toAbsolutePath());
		System.err.println(json);
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
}
