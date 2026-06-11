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

public class ChiefComplaintBackNeckSampleTest {
	
	public static final String USER_VALUE_JSON = "{\"en\":\"►<b>Back & Neck pain</b>: <br/>• Since -  3 Days.<br/>• Site - Neck.<br/>• Pain radiates to - Abdomen.<br/>• Timing - Morning, Evening, Night.<br/>• Patient has recently lifted heavy objects or twisted their back/neck.<br/>• Aggravating factor(s) - Postural change, Cough/sneezing, Pain worse at rest.<br/>• H/o specific illness - Trauma history - Involved area - Back & Neck.<br/>• Recent change of work - Patient has no recent h/o lifting heavy objects or twisting their back/neck.<br/>• Recent change of seat - Patient has recently had change in seating.<br/>• Prior treatment sought - Test.<br/>• Additional information - Test.<br/>►<b> Associated symptoms</b>:  <br/>• Patient reports -<br/> Abdominal pain,  Anxiety,  Depressed mood,  Fever,  Gait abnormalities,  Headache,  Involuntary passing of urine/Urine leakage,  Muscle spasm,  Numbness/weakness affecting one or both leg,  Other ►Test,  Sweating,  Weight change<br/> \",\"l-en\":\"►Back & Neck pain::● Since when have you had this symptom?*<br/>• 3 Days<br/>● Which part of the neck/back do you feel pain?*<br/>•Neck<br/>● Does the pain go anywhere?*<br/>•  Yes<br/>▻Abdomen<br/>● In a day, when do you feel pain the most?<br/>•Morning,Evening,Night<br/>● Have you lifted a heavy object or twisted your back/neck?*<br/>•Yes<br/>● What worsens the pain?*<br/>•Postural change,Cough/sneezing,Pain worse at rest<br/>● Have you ever been diagnosed with any of the following specific diseases?<br/>•  Trauma history<br/>▻ Involved area<br/>▻Back & Neck<br/>● Have you recently had a change in your work?<br/>•Yes<br/>● Have you recently had a change in seating position?<br/>•Yes<br/>● Have you taken any treatment (including self-medication or home remedies) or seen any health provider for this problem before coming here today?<br/>•Test<br/>● Additional information<br/>•Test<br/>►Do you have the following symptom(s)?*::•Abdominal pain<br/>•Anxiety<br/>•Depressed mood<br/>•Fever<br/>•Gait abnormalities<br/>•Headache<br/>•Involuntary passing of urine/Urine leakage<br/>•Muscle spasm<br/>•Numbness/weakness affecting one or both leg<br/>● Other  ▻Test<br/>•Sweating<br/>•Weight change (kg)<br/>\"}";
	
	private static final String OBS_UUID = "sample-obs-163212-back-neck";
	
	private final FhirContext fhirContext = FhirContext.forR4();
	
	@Test
	public void printFhirOutput() throws Exception {
		Date encounterDate = parseDate("2025-06-08T10:00:00+05:30");
		
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
			System.err.println("reportCount=" + parsed.getAssociatedSymptoms().getReports().size());
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
		Path out = Paths.get("target", "chief_complaint_back_neck_bundle.json");
		Files.createDirectories(out.getParent());
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
		System.err.println("Written to: " + out.toAbsolutePath());
		System.err.println(json);
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
}
