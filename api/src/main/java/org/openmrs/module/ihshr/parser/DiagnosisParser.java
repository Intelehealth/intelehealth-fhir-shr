package org.openmrs.module.ihshr.parser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.openmrs.module.ihshr.domain.ParsedDiagnosis;

/**
 * Parses diagnosis obs {@code value_text} (concept 163219) into {@link ParsedDiagnosis} for
 * {@link org.openmrs.module.ihshr.fhir.DiagnosisTransfer}.
 * <p>
 * Two input shapes:
 * <ul>
 * <li>Structured: {@code 82272006::Acute rhinitis:Primary & Confirmed} — optional SNOMED/ICD code,
 * display name, encounter rank (Primary/Secondary), and verification certainty after {@code &}.</li>
 * <li>JSON wrapper: {@code "diagnosis":"...", "type":"Provisional"} — {@code type} is certainty
 * when the inner diagnosis string has no {@code &} qualifier segment.</li>
 * </ul>
 */
public class DiagnosisParser {
	
	public ParsedDiagnosis parse(String rawValueText) {
		String raw = StringUtils.trimToEmpty(rawValueText);
		if (raw.isEmpty()) {
			return null;
		}
		
		if (raw.startsWith("{") && raw.endsWith("}")) {
			return parseJson(raw);
		}
		return parseStructured(raw, null);
	}
	
	private ParsedDiagnosis parseJson(String jsonString) {
		JSONObject json = new JSONObject(jsonString);
		String diagnosis = StringUtils.trimToEmpty(json.optString("diagnosis"));
		if (diagnosis.isEmpty()) {
			return null;
		}
		
		// In legacy JSON payloads, "type" often means certainty (Provisional/Confirmed), not Primary/Secondary.
		String jsonType = StringUtils.trimToEmpty(json.optString("type"));
		
		if (diagnosis.contains("::")) {
			return parseStructured(diagnosis, jsonType);
		}
		
		ParsedDiagnosis parsed = new ParsedDiagnosis();
		parsed.setDiagnosisText(cleanDiagnosisText(diagnosis));
		parsed.setDiagnosisCategory(normalizeCategory(jsonType));
		return parsed;
	}
	
	/**
	 * Parses {@code [code::]name[:Primary & Confirmed]}. {@code fallbackCategory} applies when
	 * certainty is not present after {@code &} (e.g. from JSON {@code type}).
	 */
	private ParsedDiagnosis parseStructured(String structured, String fallbackCategory) {
		ParsedDiagnosis parsed = new ParsedDiagnosis();
		String text = StringUtils.trimToEmpty(structured);
		
		if (text.contains("::")) {
			String[] codeAndRest = text.split("::", 2);
			parsed.setCode(StringUtils.trimToNull(codeAndRest[0]));
			text = StringUtils.trimToEmpty(codeAndRest[1]);
		}
		
		String diagnosisText = text;
		String diagnosisType = null;
		String diagnosisCategory = fallbackCategory;
		
		if (text.contains(":")) {
			String[] nameAndQual = text.split(":", 2);
			diagnosisText = StringUtils.trimToEmpty(nameAndQual[0]);
			String qualifiersPart = StringUtils.trimToEmpty(nameAndQual[1]);
			if (!qualifiersPart.isEmpty()) {
				// "Primary & Confirmed" → rank type, then verification category.
				String[] qualifiers = qualifiersPart.split("\\s*&\\s*");
				if (qualifiers.length > 0) {
					diagnosisType = qualifiers[0];
				}
				if (qualifiers.length > 1) {
					diagnosisCategory = qualifiers[1];
				}
			}
		}
		
		parsed.setDiagnosisText(cleanDiagnosisText(diagnosisText));
		parsed.setDiagnosisType(normalizeType(diagnosisType));
		parsed.setDiagnosisCategory(normalizeCategory(diagnosisCategory));
		if (parsed.getDiagnosisText() == null) {
			return null;
		}
		return parsed;
	}
	
	private static String cleanDiagnosisText(String s) {
		String cleaned = StringUtils.trimToEmpty(s).replaceAll("\\s+", " ");
		cleaned = cleaned.replaceAll("\\.+$", "");
		return StringUtils.trimToNull(cleaned);
	}
	
	/**
	 * Only Primary and Secondary are kept; other rank labels are ignored (rank falls back to 3+
	 * upstream).
	 */
	private static String normalizeType(String s) {
		String v = StringUtils.trimToEmpty(s).toLowerCase();
		if ("primary".equals(v)) {
			return "Primary";
		}
		if ("secondary".equals(v)) {
			return "Secondary";
		}
		return null;
	}
	
	/**
	 * Maps UI certainty labels to FHIR condition-ver-status codes used by
	 * {@link org.openmrs.module.ihshr.fhir.DiagnosisConditionBuilder}.
	 */
	private static String normalizeCategory(String s) {
		String v = StringUtils.trimToEmpty(s).toLowerCase();
		if ("provisional".equals(v)) {
			return "provisional";
		}
		if ("confirmed".equals(v)) {
			return "confirmed";
		}
		if ("under evaluation".equals(v) || "under-evaluation".equals(v) || "under_evaluation".equals(v)) {
			return "unconfirmed";
		}
		return null;
	}
}
