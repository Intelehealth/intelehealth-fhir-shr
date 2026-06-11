package org.openmrs.module.ihshr.utils;

public final class FollowUpConstants {
	
	public static final int FOLLOW_UP_CONCEPT_ID = 163345;
	
	public static final String OBS_IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String CONCEPT_CODE_SYSTEM = "urn:intelehealth:openmrs-concept";
	
	public static final String CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";
	
	public static final String CATEGORY_CODE = "survey";
	
	public static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	
	public static final String SNOMED_FOLLOW_UP_CODE = "390906007";
	
	public static final String SNOMED_FOLLOW_UP_DISPLAY = "Follow-up encounter (procedure)";
	
	public static String observationIdentifier(String obsUuid) {
		return obsUuid + "::follow-up";
	}
	
	public static String conceptSearchToken() {
		return CONCEPT_CODE_SYSTEM + "|" + FOLLOW_UP_CONCEPT_ID;
	}
	
	private FollowUpConstants() {
	}
	
}
