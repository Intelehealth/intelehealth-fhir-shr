package org.openmrs.module.ihshr.utils;

public final class ChiefComplaintConstants {
	
	public static final int CHIEF_COMPLAINT_CONCEPT_ID = 163212;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String CONDITION_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-category";
	
	public static final String CONDITION_CATEGORY_CODE = "encounter-diagnosis";
	
	public static final String VERIFICATION_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
	
	public static final String VERIFICATION_STATUS_CODE = "unconfirmed";
	
	public static final String CLINICAL_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
	
	public static final String CLINICAL_STATUS_CODE = "active";
	
	public static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	
	public static final String ASSOC_OBS_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";
	
	public static final String ASSOC_OBS_CATEGORY_CODE = "exam";
	
	public static final String PRESENT_CODE = "52101004";
	
	public static final String PRESENT_DISPLAY = "Present (qualifier value)";
	
	public static final String NEGATIVE_CODE = "260385009";
	
	public static final String NEGATIVE_DISPLAY = "Negative (qualifier value)";
	
	public static final String INTERPRETATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
	
	public static final String INTERPRETATION_POS = "POS";
	
	public static final String INTERPRETATION_NEG = "NEG";
	
	public static final String ANOREXIA_CODE = "79890006";
	
	public static final String ANOREXIA_DISPLAY = "Loss of appetite (finding)";
	
	public static String conditionIdentifier(String obsUuid, int index) {
		return obsUuid + "::cc-" + index;
	}
	
	public static String associatedPositiveIdentifier(String obsUuid, int index) {
		return obsUuid + "::assoc-pos-" + index;
	}
	
	public static String associatedNegativeIdentifier(String obsUuid, int index) {
		return obsUuid + "::assoc-neg-" + index;
	}
	
	private ChiefComplaintConstants() {
	}
	
}
