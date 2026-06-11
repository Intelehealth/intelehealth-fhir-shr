package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;
import org.openmrs.module.ihshr.parser.MedicalHistoryParser;

public final class MedicalHistoryMatcher {
	
	private MedicalHistoryMatcher() {
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId,
		    StructuredObsConceptSettings.medicalHistoryConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (StringUtils.isBlank(valueText)) {
			return false;
		}
		return matchesClinicalHtml(ClinicalJsonValueTexts.extractClinicalHtml(valueText));
	}
	
	public static boolean matchesClinicalHtml(String clinical) {
		if (StringUtils.isBlank(clinical)) {
			return false;
		}
		if (ChiefComplaintMatcher.matchesClinicalHtml(clinical) || PhysicalExamMatcher.matchesClinicalHtml(clinical)) {
			return false;
		}
		if (!clinical.contains(" - ")) {
			return false;
		}
		return !new MedicalHistoryParser().parse(clinical).isEmpty();
	}
	
	public static boolean isMedicalHistoryObs(CompletedRecord record) {
		return record != null && matchesConceptId(record.getConceptId());
	}
	
	public static String describeMatch(CompletedRecord record) {
		if (record == null) {
			return "no-record";
		}
		if (matchesConceptId(record.getConceptId())) {
			return "medical-history-concept-163210";
		}
		if (ClinicalJsonValueTexts.hasEnClinicalJson(record.getValueText())) {
			return "medical-history-value_text-json-en";
		}
		if (matchesClinicalHtml(ClinicalJsonValueTexts.extractClinicalHtml(record.getValueText()))) {
			return "medical-history-value_text-html";
		}
		return "none";
	}
	
}
