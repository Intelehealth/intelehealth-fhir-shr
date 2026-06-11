package org.openmrs.module.ihshr.parser;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Physical examination {@code obs.value_text} JSON wrapper helpers (delegates to
 * {@link ClinicalJsonValueTexts}).
 */
public final class PhysicalExamValueTexts {
	
	private static final Pattern PICTURE_MARKER = Pattern.compile(",?\\s*\\[picture taken\\]\\s*\\.?",
	    Pattern.CASE_INSENSITIVE);
	
	public static final String CLINICAL_LOCALE_KEY = ClinicalJsonValueTexts.CLINICAL_LOCALE_KEY;
	
	private PhysicalExamValueTexts() {
	}
	
	public static String extractClinicalHtml(String raw) {
		return ClinicalJsonValueTexts.extractClinicalHtml(raw);
	}
	
	public static boolean isJsonValue(String raw) {
		return ClinicalJsonValueTexts.isJsonValue(raw);
	}
	
	public static boolean hasEnClinicalJson(String raw) {
		return ClinicalJsonValueTexts.hasEnClinicalJson(raw);
	}
	
	public static String normalizeHtml(String html) {
		return ClinicalJsonValueTexts.normalizeHtml(html);
	}
	
	/** Removes {@code [picture taken]} placeholders; images are sent separately. */
	public static String stripPictureTakenMarkers(String text) {
		if (StringUtils.isBlank(text)) {
			return text;
		}
		return PICTURE_MARKER.matcher(text).replaceAll("");
	}
	
}
