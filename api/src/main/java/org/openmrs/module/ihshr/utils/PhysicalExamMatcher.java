package org.openmrs.module.ihshr.utils;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.PhysicalExamValueTexts;

public final class PhysicalExamMatcher {
	
	private static final Pattern CATEGORY_HEADING_HTML = Pattern.compile("<b>\\s*[^<]+?\\s*:?\\s*</b>",
	    Pattern.CASE_INSENSITIVE);
	
	private PhysicalExamMatcher() {
	}
	
	public static boolean isPhysicalExamObs(CompletedRecord record) {
		return record != null && matchesConceptId(record.getConceptId());
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId,
		    StructuredObsConceptSettings.physicalExamConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (StringUtils.isBlank(valueText)) {
			return false;
		}
		return matchesClinicalHtml(PhysicalExamValueTexts.extractClinicalHtml(valueText));
	}
	
	public static boolean matchesClinicalHtml(String html) {
		if (StringUtils.isBlank(html)) {
			return false;
		}
		return CATEGORY_HEADING_HTML.matcher(html).find();
	}
	
	public static boolean matchesFhirObservation(Observation observation) {
		if (observation == null) {
			return false;
		}
		if (observation.hasValueStringType()) {
			String value = observation.getValueStringType().getValueAsString();
			if (matchesValueText(value)) {
				return true;
			}
		}
		return matchesCodeableConcept(observation.getCode());
	}
	
	public static boolean matchesCodeableConcept(CodeableConcept code) {
		if (code == null) {
			return false;
		}
		if (code.hasText() && StringUtils.containsIgnoreCase(code.getText(), "PHYSICAL EXAM")) {
			return true;
		}
		for (Coding coding : code.getCoding()) {
			if (PhysicalExamConstants.EXAM_PROCEDURE_CODE.equals(coding.getCode())) {
				return true;
			}
			if (coding.hasDisplay() && StringUtils.containsIgnoreCase(coding.getDisplay(), "PHYSICAL EXAM")) {
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
			return "conceptId=" + record.getConceptId();
		}
		if (PhysicalExamValueTexts.hasEnClinicalJson(record.getValueText())) {
			return "value_text-json-en";
		}
		if (matchesClinicalHtml(PhysicalExamValueTexts.extractClinicalHtml(record.getValueText()))) {
			return "value_text-html";
		}
		return "no-match";
	}
	
}
