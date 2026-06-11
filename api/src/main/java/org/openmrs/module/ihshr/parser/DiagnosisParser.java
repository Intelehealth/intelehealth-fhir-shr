package org.openmrs.module.ihshr.parser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.openmrs.module.ihshr.domain.ParsedDiagnosis;

/**
 * Parses diagnosis strings from obs.value_text. Supports: -
 * "82272006::Acute rhinitis:Primary & Confirmed" - JSON wrappers: {"diagnosis":"...",
 * "type":"Provisional"}
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
		
		// In legacy JSON payloads, "type" often means certainty (Provisional/Confirmed).
		String jsonType = StringUtils.trimToEmpty(json.optString("type"));
		
		if (diagnosis.contains("::")) {
			return parseStructured(diagnosis, jsonType);
		}
		
		ParsedDiagnosis parsed = new ParsedDiagnosis();
		parsed.setDiagnosisText(cleanDiagnosisText(diagnosis));
		parsed.setDiagnosisCategory(normalizeCategory(jsonType));
		return parsed;
	}
	
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
