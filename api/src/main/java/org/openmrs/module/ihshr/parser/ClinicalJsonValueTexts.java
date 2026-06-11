package org.openmrs.module.ihshr.parser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Shared extraction of clinical HTML from multilingual {@code obs.value_text} JSON wrappers. Only
 * values with an {@code en} key are processed; legacy plain HTML is skipped.
 */
public final class ClinicalJsonValueTexts {
	
	public static final String CLINICAL_LOCALE_KEY = "en";
	
	private ClinicalJsonValueTexts() {
	}
	
	public static String extractClinicalHtml(String raw) {
		if (StringUtils.isBlank(raw)) {
			return "";
		}
		String trimmed = raw.trim();
		if (!trimmed.startsWith("{")) {
			return trimmed;
		}
		try {
			JSONObject json = new JSONObject(trimmed);
			if (json.has(CLINICAL_LOCALE_KEY) && !json.isNull(CLINICAL_LOCALE_KEY)) {
				return json.getString(CLINICAL_LOCALE_KEY);
			}
			return "";
		}
		catch (JSONException e) {
			return trimmed;
		}
	}
	
	public static boolean isJsonValue(String raw) {
		return raw != null && raw.trim().startsWith("{");
	}
	
	public static boolean hasEnClinicalJson(String raw) {
		if (!isJsonValue(raw)) {
			return false;
		}
		try {
			JSONObject json = new JSONObject(raw.trim());
			return json.has(CLINICAL_LOCALE_KEY) && !json.isNull(CLINICAL_LOCALE_KEY)
			        && StringUtils.isNotBlank(json.getString(CLINICAL_LOCALE_KEY));
		}
		catch (JSONException e) {
			return false;
		}
	}
	
	public static String normalizeHtml(String html) {
		if (html == null) {
			return "";
		}
		return html.replace("►", "").replace("\u25ba", "");
	}
	
}
