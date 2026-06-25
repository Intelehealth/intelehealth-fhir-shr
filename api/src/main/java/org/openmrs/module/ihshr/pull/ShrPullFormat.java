package org.openmrs.module.ihshr.pull;

import org.apache.commons.lang3.StringUtils;

/**
 * Allowed {@code format} query values for SHR pull responses (doc §4 / §10).
 */
public enum ShrPullFormat {
	
	ENVELOPE("envelope"), FHIR("fhir"), FHIR_MERGED("fhir-merged"), TIMELINE("timeline");
	
	private final String paramValue;
	
	ShrPullFormat(String paramValue) {
		this.paramValue = paramValue;
	}
	
	public String getParamValue() {
		return paramValue;
	}
	
	public boolean isFhirBundle() {
		return this == FHIR || this == FHIR_MERGED;
	}
	
	public static ShrPullFormat parse(String value) {
		if (StringUtils.isBlank(value)) {
			return ENVELOPE;
		}
		String normalized = value.trim().toLowerCase();
		if ("envelope".equals(normalized)) {
			return ENVELOPE;
		}
		if ("fhir".equals(normalized)) {
			return FHIR;
		}
		if ("fhir-merged".equals(normalized)) {
			return FHIR_MERGED;
		}
		if ("timeline".equals(normalized)) {
			return TIMELINE;
		}
		throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Unknown format: " + value
		        + ". Allowed: envelope, fhir, fhir-merged, timeline");
	}
	
	public static ShrPullFormat fromRequest(ShrHistoryRequest request) {
		if (request == null || StringUtils.isBlank(request.getFormat())) {
			return ENVELOPE;
		}
		return parse(request.getFormat());
	}
}
