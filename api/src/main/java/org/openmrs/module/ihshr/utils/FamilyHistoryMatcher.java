package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;

public final class FamilyHistoryMatcher {
	
	private FamilyHistoryMatcher() {
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId,
		    StructuredObsConceptSettings.familyHistoryConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (!ClinicalJsonValueTexts.hasEnClinicalJson(valueText)) {
			return false;
		}
		String clinical = ClinicalJsonValueTexts.extractClinicalHtml(valueText);
		if (StringUtils.isBlank(clinical)) {
			return false;
		}
		String lower = clinical.toLowerCase();
		return lower.contains("family history") && clinical.contains(":");
	}
	
	public static boolean isFamilyHistoryObs(CompletedRecord record) {
		return record != null && matchesConceptId(record.getConceptId());
	}
	
	public static String describeMatch(CompletedRecord record) {
		if (record == null) {
			return "no-record";
		}
		if (matchesConceptId(record.getConceptId())) {
			return "family-history-concept-163211";
		}
		if (ClinicalJsonValueTexts.hasEnClinicalJson(record.getValueText())) {
			return "family-history-value_text-json-en";
		}
		return "none";
	}
	
}
