package org.openmrs.module.ihshr.parser;

/**
 * Chief complaint {@code obs.value_text} JSON wrapper helpers (delegates to
 * {@link ClinicalJsonValueTexts}).
 */
public final class ChiefComplaintValueTexts {
	
	public static final String CLINICAL_LOCALE_KEY = ClinicalJsonValueTexts.CLINICAL_LOCALE_KEY;
	
	private ChiefComplaintValueTexts() {
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
	
}
