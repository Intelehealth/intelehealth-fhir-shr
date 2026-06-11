package org.openmrs.module.ihshr.backlog;

import org.apache.commons.lang3.StringUtils;

/**
 * Lookup backlog artifact (doc §8.6 {@code intelehealth_unmapped_&lt;artifact&gt;}).
 */
public enum UnmappedTermArtifact {
	
	CHIEF_COMPLAINT("chief_complaint", "chief-complaint-mappings.json"),
	
	PHYSICAL_EXAM("physical_exam", "physical-exam-mappings.json"),
	
	PHYSICAL_EXAM_BODYSITE("physical_exam_bodysite", "exam-bodysite-mappings.json"),
	
	FAMILY_HISTORY_CONDITION("family_history_condition", "family-history-conditions.json"),
	
	FAMILY_HISTORY_RELATIONSHIP("family_history_relationship", "family-relationships.json"),
	
	MEDICAL_HISTORY_CONDITION("medical_history_condition", "family-history-conditions.json"),
	
	REFERRAL_SPECIALTY("referral_specialty", "referral-specialty-mappings.json"),
	
	UNKNOWN("unknown", null);
	
	private final String code;
	
	private final String defaultLookupFile;
	
	UnmappedTermArtifact(String code, String defaultLookupFile) {
		this.code = code;
		this.defaultLookupFile = defaultLookupFile;
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDefaultLookupFile() {
		return defaultLookupFile;
	}
	
	public static UnmappedTermArtifact fromLookupFile(String lookupFile) {
		if (StringUtils.isBlank(lookupFile)) {
			return UNKNOWN;
		}
		String name = lookupFile.trim();
		for (UnmappedTermArtifact artifact : values()) {
			if (name.equals(artifact.defaultLookupFile)) {
				return artifact;
			}
		}
		return UNKNOWN;
	}
	
	public static UnmappedTermArtifact fromCode(String code) {
		if (StringUtils.isBlank(code)) {
			return UNKNOWN;
		}
		for (UnmappedTermArtifact artifact : values()) {
			if (artifact.code.equalsIgnoreCase(code.trim())) {
				return artifact;
			}
		}
		return UNKNOWN;
	}
	
}
