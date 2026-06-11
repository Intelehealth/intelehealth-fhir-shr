package org.openmrs.module.ihshr.utils;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.ChiefComplaintValueTexts;

public final class ChiefComplaintMatcher {
	
	private static final Pattern SYMPTOM_BLOCK = Pattern.compile("►\\s*<b>", Pattern.CASE_INSENSITIVE);
	
	private ChiefComplaintMatcher() {
	}
	
	public static boolean isChiefComplaintObs(CompletedRecord record) {
		return record != null && matchesConceptId(record.getConceptId());
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId,
		    StructuredObsConceptSettings.chiefComplaintConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (StringUtils.isBlank(valueText)) {
			return false;
		}
		return matchesClinicalHtml(ChiefComplaintValueTexts.extractClinicalHtml(valueText));
	}
	
	public static boolean matchesClinicalHtml(String html) {
		if (StringUtils.isBlank(html)) {
			return false;
		}
		if (SYMPTOM_BLOCK.matcher(html).find()) {
			return true;
		}
		String upper = html.toUpperCase();
		return upper.contains("CURRENT COMPLAINT") || upper.contains("CHIEF COMPLAINT")
		        || upper.contains("ASSOCIATED SYMPTOMS");
	}
	
	public static boolean matchesFhirObservation(Observation observation) {
		if (observation == null) {
			return false;
		}
		if (observation.hasValueStringType()) {
			if (matchesValueText(observation.getValueStringType().getValueAsString())) {
				return true;
			}
		}
		return matchesCodeableConcept(observation.getCode());
	}
	
	public static boolean matchesCodeableConcept(CodeableConcept code) {
		if (code == null) {
			return false;
		}
		if (code.hasText() && StringUtils.containsIgnoreCase(code.getText(), "COMPLAINT")) {
			return true;
		}
		for (Coding coding : code.getCoding()) {
			if (coding.hasDisplay() && StringUtils.containsIgnoreCase(coding.getDisplay(), "COMPLAINT")) {
				return true;
			}
		}
		return false;
	}
	
	public static String describeMatch(CompletedRecord record) {
		if (record == null) {
			return "no-record";
		}
		if (matchesConceptId(record.getConceptId())) {
			return "chief-complaint-concept-163212";
		}
		if (ChiefComplaintValueTexts.hasEnClinicalJson(record.getValueText())) {
			return "value_text-json-en";
		}
		if (matchesClinicalHtml(ChiefComplaintValueTexts.extractClinicalHtml(record.getValueText()))) {
			return "value_text-symptom-blocks";
		}
		return "none";
	}
	
}
