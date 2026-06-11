package org.openmrs.module.ihshr.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.ParsedAssociatedSymptoms;
import org.openmrs.module.ihshr.domain.ParsedComplaint;
import org.openmrs.module.ihshr.domain.ParsedComplaintBundle;
import org.openmrs.module.ihshr.fhir.ChiefComplaintBuildResult;
import org.openmrs.module.ihshr.fhir.ChiefComplaintTransfer;
import org.openmrs.module.ihshr.fhir.VisitPushResourceIds;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;
import org.openmrs.module.ihshr.utils.ChiefComplaintMatcher;

public class ChiefComplaintParserTest {
	
	public static final String THREE_SYMPTOM_INPUT = "►<b>Fatigue and General weakness</b>: <br/>• Duration - 3 Days.<br/>"
	        + "• Timing - All day.<br/>• Eating habits - Amount - Small.<br/> ►<b>Fever</b>: <br/>"
	        + "• Duration - 2 Days.<br/>• Nature of fever - All day/ Constant.<br/> ►<b>Headache</b>: <br/>"
	        + "• Duration - 2 Days.<br/>• Site - Diffuse.<br/>";
	
	public static final String COLD_SNEEZING_WITH_ASSOC = "►<b>Cold, Sneezing</b>: <br/>• Precipitating factors - Cold weather.<br/>"
	        + "• Prior treatment sought - None.<br/>►<b> Associated symptoms</b>:  <br/>• Patient reports -<br/> "
	        + "Itchy throat,  Nasal congestion/Stuffy nose <br/>• Patient denies -<br/> Body pain<br/> ";
	
	public static final String ABDOMINAL_WITH_ASSOC = "►<b>Abdominal Pain</b>: <br/>• Site - Upper (C) - Epigastric.<br/>"
	        + "• Pain radiates to - Upper (R) - Right Hypochondrium.<br/>• 15 Days.<br/>• Onset - Gradual.<br/>"
	        + "• Character of the pain - Colicky / Intermittent.<br/>• Severity - Mild, 1-3.<br/> "
	        + "►<b>Associated symptoms</b>: <br/>• Patient reports -<br/> Anorexia <br/>• Patient denies -<br/> "
	        + "Nausea, Vomiting, Diarrhea, Constipation, Fever, Abdominal distention/Bloating, Belching/Burping, "
	        + "Passing gas, Color change in stool, Blood in stool, change in frequency of urination, "
	        + "Color change in urine, Hiccups<br/><br/>";
	
	private static final String OBS_UUID = "obs-uuid-163212";
	
	@Test
	public void parse_threeSymptoms_extractsNameAndDurationOnly() throws Exception {
		Date encounterDate = parseDate("2025-04-30T11:30:00+05:30");
		ParsedComplaintBundle bundle = new ChiefComplaintParser().parse(THREE_SYMPTOM_INPUT, encounterDate);
		
		List<ParsedComplaint> complaints = bundle.getComplaints();
		assertEquals(3, complaints.size());
		
		assertEquals("Fatigue and General weakness", complaints.get(0).getSymptom());
		assertEquals("3 Days", complaints.get(0).getDurationText());
		assertNotNull(complaints.get(0).getOnsetDateTime());
		
		assertEquals("Fever", complaints.get(1).getSymptom());
		assertEquals("2 Days", complaints.get(1).getDurationText());
		
		assertEquals("Headache", complaints.get(2).getSymptom());
		assertEquals("2 Days", complaints.get(2).getDurationText());
		assertNull(bundle.getAssociatedSymptoms());
		assertTrue(bundle.getSharedNoteText().contains("Fatigue"));
	}
	
	@Test
	public void parse_sinceLabel_extractsDurationAndOnset() throws Exception {
		String html = "►<b>Back & Neck pain</b>: <br/>• Since -  3 Days.<br/>• Severity - Mild.<br/>";
		Date encounterDate = parseDate("2025-04-30T11:30:00+05:30");
		ParsedComplaintBundle bundle = new ChiefComplaintParser().parse(html, encounterDate);
		
		assertEquals(1, bundle.getComplaints().size());
		assertEquals("Back & Neck pain", bundle.getComplaints().get(0).getSymptom());
		assertEquals("3 Days", bundle.getComplaints().get(0).getDurationText());
		assertNotNull(bundle.getComplaints().get(0).getOnsetDateTime());
		
		Date onset = bundle.getComplaints().get(0).getOnsetDateTime();
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.setTime(onset);
		assertEquals(27, cal.get(java.util.Calendar.DAY_OF_MONTH));
	}
	
	@Test
	public void parse_sinceLabel_lowercaseAndHours_extractsDuration() throws Exception {
		String html = "►<b>Dry mouth</b>: <br/>• since -  9 Hours.<br/>";
		Date encounterDate = parseDate("2025-06-03T14:30:00+05:30");
		ParsedComplaintBundle bundle = new ChiefComplaintParser().parse(html, encounterDate);
		
		assertEquals(1, bundle.getComplaints().size());
		assertEquals("Dry mouth", bundle.getComplaints().get(0).getSymptom());
		assertEquals("9 Hours", bundle.getComplaints().get(0).getDurationText());
		assertNotNull(bundle.getComplaints().get(0).getOnsetDateTime());
	}
	
	@Test
	public void parseDurationToOnset_subtractsDaysFromEncounterStartOfDay() throws Exception {
		Date encounterDate = parseDate("2025-04-30T11:30:00+05:30");
		Date onset = ChiefComplaintParser.parseDurationToOnset("3 Days", encounterDate);
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.setTime(onset);
		assertEquals(2025, cal.get(java.util.Calendar.YEAR));
		assertEquals(java.util.Calendar.APRIL, cal.get(java.util.Calendar.MONTH));
		assertEquals(27, cal.get(java.util.Calendar.DAY_OF_MONTH));
		assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY));
	}
	
	@Test
	public void parse_abdominalWithAssociated_extractsReportsAndDenies() {
		Date encounterDate = new Date();
		ParsedComplaintBundle bundle = new ChiefComplaintParser().parse(ABDOMINAL_WITH_ASSOC, encounterDate);
		
		assertEquals(1, bundle.getComplaints().size());
		assertEquals("Abdominal Pain", bundle.getComplaints().get(0).getSymptom());
		assertEquals("15 Days", bundle.getComplaints().get(0).getDurationText());
		
		ParsedAssociatedSymptoms assoc = bundle.getAssociatedSymptoms();
		assertNotNull(assoc);
		assertTrue(assoc.getReports().size() >= 1);
		assertTrue(assoc.getReports().contains("Anorexia"));
		assertTrue(assoc.getDenies().size() >= 13);
		assertTrue(assoc.getDenies().contains("Nausea"));
		assertTrue(assoc.getDenies().contains("Hiccups"));
	}
	
	@Test
	public void parse_coldSneezingPlainHtml_extractsComplaintAndAssociated() {
		Date encounterDate = new Date();
		ParsedComplaintBundle bundle = new ChiefComplaintParser().parse(COLD_SNEEZING_WITH_ASSOC, encounterDate);
		
		assertEquals(1, bundle.getComplaints().size());
		assertEquals("Cold, Sneezing", bundle.getComplaints().get(0).getSymptom());
		assertNull(bundle.getComplaints().get(0).getDurationText());
		
		ParsedAssociatedSymptoms assoc = bundle.getAssociatedSymptoms();
		assertNotNull(assoc);
		assertEquals(2, assoc.getReports().size());
		assertEquals(1, assoc.getDenies().size());
		assertTrue(assoc.getReports().contains("Itchy throat"));
		assertTrue(assoc.getReports().contains("Nasal congestion/Stuffy nose"));
		assertTrue(assoc.getDenies().contains("Body pain"));
	}
	
	@Test
	public void buildResult_plainHtml_emitsConditionsAndAssociatedObservations() throws Exception {
		org.hl7.fhir.r4.model.Observation source = new org.hl7.fhir.r4.model.Observation();
		source.getSubject().setReference("Patient/test");
		source.getEncounter().setReference("Encounter/test");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		
		ChiefComplaintBuildResult result = new ChiefComplaintTransfer().build(source, OBS_UUID, COLD_SNEEZING_WITH_ASSOC);
		assertEquals(1, result.getConditions().size());
		assertEquals("Cold, Sneezing", result.getConditions().get(0).getCode().getText());
		assertEquals(3, result.getAssociatedSymptomObservations().size()); // 2 reported + 1 denied
	}
	
	@Test
	public void buildResult_multiChiefComplaint_sharedAssociated_emitsOncePerAssocWithMultiFocus() throws Exception {
		String html = "►<b>Abdominal Pain</b>: <br/>• 15 Days.<br/> ►<b>Fever</b>: <br/>• Duration - 2 Days.<br/>"
		        + ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC.substring(ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC
		                .indexOf("►<b>Associated symptoms</b>"));
		org.hl7.fhir.r4.model.Observation source = new org.hl7.fhir.r4.model.Observation();
		source.getSubject().setReference("Patient/test");
		ChiefComplaintBuildResult result = new ChiefComplaintTransfer().build(source, OBS_UUID, html);
		assertEquals(2, result.getConditions().size());
		assertEquals(14, result.getAssociatedSymptomObservations().size());
		assertEquals(16, result.totalResourceCount());
		org.hl7.fhir.r4.model.Observation anorexia = result.getAssociatedSymptomObservations().get(0);
		assertEquals(2, anorexia.getFocus().size());
		assertEquals("Condition/" + VisitPushResourceIds.resolvePutResourceId(result.getConditions().get(0)), anorexia
		        .getFocus().get(0).getReference());
		assertEquals("Condition/" + VisitPushResourceIds.resolvePutResourceId(result.getConditions().get(1)), anorexia
		        .getFocus().get(1).getReference());
	}
	
	@Test
	public void buildResult_emitsConditionsAndAssociatedObservations() throws Exception {
		org.hl7.fhir.r4.model.Observation source = new org.hl7.fhir.r4.model.Observation();
		source.getSubject().setReference("Patient/test");
		source.getEncounter().setReference("Encounter/test");
		source.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		
		String jsonValue = new JSONObject().put("en", ABDOMINAL_WITH_ASSOC).toString();
		ChiefComplaintBuildResult result = new ChiefComplaintTransfer().build(source, OBS_UUID, jsonValue);
		assertEquals(1, result.getConditions().size());
		assertEquals(14, result.getAssociatedSymptomObservations().size());
		assertEquals(ChiefComplaintConstants.conditionIdentifier(OBS_UUID, 0), result.getConditions().get(0)
		        .getIdentifierFirstRep().getValue());
		assertEquals(1, result.getAssociatedSymptomObservations().get(0).getFocus().size());
		assertEquals("Condition/" + VisitPushResourceIds.resolvePutResourceId(result.getConditions().get(0)), result
		        .getAssociatedSymptomObservations().get(0).getFocusFirstRep().getReference());
	}
	
	@Test
	public void matcher_detectsSymptomBlockFormat() throws Exception {
		assertTrue(ChiefComplaintMatcher.matchesClinicalHtml(THREE_SYMPTOM_INPUT));
		String json = new JSONObject().put("en", THREE_SYMPTOM_INPUT).toString();
		assertTrue(ChiefComplaintMatcher.matchesValueText(json));
		assertTrue(ChiefComplaintMatcher.matchesValueText(THREE_SYMPTOM_INPUT));
		assertTrue(ChiefComplaintMatcher.matchesValueText(COLD_SNEEZING_WITH_ASSOC));
		assertTrue(ChiefComplaintMatcher.matchesConceptId(163212));
	}
	
	private static Date parseDate(String iso) throws Exception {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		return format.parse(iso);
	}
	
}
