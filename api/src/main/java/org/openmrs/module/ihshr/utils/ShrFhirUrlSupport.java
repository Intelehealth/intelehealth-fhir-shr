package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes central SHR FHIR base URLs from global properties / classpath defaults.
 */
public final class ShrFhirUrlSupport {
	
	private ShrFhirUrlSupport() {
	}
	
	public static String resolveShrFhirBaseUrl() {
		return normalizeShrFhirBaseUrl(IhshrPropertyResolver.resolve("opencr.shr.url"));
	}
	
	/**
	 * Ensures pull/push use {@code /shr/fhir/}. Legacy GPs such as {@code http://host:6001/fhir/}
	 * are rewritten to {@code http://host:6001/shr/fhir/}.
	 */
	public static String normalizeShrFhirBaseUrl(String url) {
		if (StringUtils.isBlank(url)) {
			return "";
		}
		String normalized = url.trim();
		if (!normalized.contains("/shr/fhir") && normalized.matches("(?i).*/fhir/?$")) {
			normalized = normalized.replaceAll("/fhir/?$", "/shr/fhir/");
		}
		if (!normalized.endsWith("/")) {
			normalized += "/";
		}
		return normalized;
	}
}
