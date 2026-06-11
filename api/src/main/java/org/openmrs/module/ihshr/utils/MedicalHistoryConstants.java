package org.openmrs.module.ihshr.utils;

public final class MedicalHistoryConstants {
	
	public static final int PATIENT_MEDICAL_HISTORY_CONCEPT_ID = 163210;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String OBS_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";
	
	public static final String CONDITION_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-category";
	
	public static final String CONDITION_CATEGORY_CODE = "problem-list-item";
	
	public static final String CLINICAL_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
	
	public static final String VERIFICATION_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
	
	public static final String ALLERGY_CLINICAL_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
	
	public static final String ALLERGY_VERIFICATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";
	
	public static String topicIdentifier(String obsUuid, String topicSlug, int index) {
		if (index <= 0) {
			return obsUuid + "::topic-" + topicSlug;
		}
		return obsUuid + "::topic-" + topicSlug + "-" + index;
	}
	
	private MedicalHistoryConstants() {
	}
	
}
