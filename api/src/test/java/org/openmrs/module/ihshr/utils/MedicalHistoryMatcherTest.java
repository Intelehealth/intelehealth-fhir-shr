package org.openmrs.module.ihshr.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.ChiefComplaintParserTest;
import org.openmrs.module.ihshr.parser.MedicalHistoryParserTest;

public class MedicalHistoryMatcherTest {
	
	private static final String BACK_NECK_JSON = new JSONObject().put(
	    "en",
	    "►<b>Back & Neck pain</b>: <br/>• Since -  3 Days.<br/>• Site - Neck.<br/>"
	            + "►<b> Associated symptoms</b>: <br/>• Patient reports - Abdominal pain<br/>").toString();
	
	@Test
	public void matchesValueText_chiefComplaintHtml_doesNotMatch() {
		assertFalse(MedicalHistoryMatcher.matchesValueText(BACK_NECK_JSON));
		assertFalse(MedicalHistoryMatcher.matchesValueText(ChiefComplaintParserTest.ABDOMINAL_WITH_ASSOC));
	}
	
	@Test
	public void matchesValueText_medicalHistoryHtml_stillMatches() {
		assertTrue(MedicalHistoryMatcher.matchesValueText(MedicalHistoryParserTest.PLAIN_QUESTION_MARK_HTML));
	}
	
	@Test
	public void isMedicalHistoryObs_chiefComplaintConcept_doesNotMatch() {
		CompletedRecord record = new CompletedRecord();
		record.setConceptId(ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID);
		record.setValueText(BACK_NECK_JSON);
		assertFalse(MedicalHistoryMatcher.isMedicalHistoryObs(record));
	}
	
	@Test
	public void isMedicalHistoryObs_medicalHistoryConcept_matchesRegardlessOfValueText() {
		CompletedRecord record = new CompletedRecord();
		record.setConceptId(MedicalHistoryConstants.PATIENT_MEDICAL_HISTORY_CONCEPT_ID);
		record.setValueText(BACK_NECK_JSON);
		assertTrue(MedicalHistoryMatcher.isMedicalHistoryObs(record));
	}
	
	@Test
	public void isChiefComplaintObs_chiefComplaintConcept_matchesRegardlessOfValueText() {
		CompletedRecord record = new CompletedRecord();
		record.setConceptId(ChiefComplaintConstants.CHIEF_COMPLAINT_CONCEPT_ID);
		record.setValueText(MedicalHistoryParserTest.PLAIN_QUESTION_MARK_HTML);
		assertTrue(ChiefComplaintMatcher.isChiefComplaintObs(record));
	}
	
}
