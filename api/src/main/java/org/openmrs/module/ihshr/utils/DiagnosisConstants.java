package org.openmrs.module.ihshr.utils;

public final class DiagnosisConstants {
	
	public static final int DIAGNOSIS_CONCEPT_ID = 163219;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:diagnosis";
	
	public static final String CONDITION_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-category";
	
	public static final String CONDITION_CATEGORY_CODE = "encounter-diagnosis";
	
	public static final String VERIFICATION_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
	
	public static final String CLINICAL_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
	
	public static final String CLINICAL_STATUS_CODE = "active";
	
	public static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	
	public static final String ICD10_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
	
	public static final String IH_NATIVE_CODE_SYSTEM = "urn:intelehealth:concept";
	
	public static final String DIAGNOSIS_ROLE_SYSTEM = "http://terminology.hl7.org/CodeSystem/diagnosis-role";
	
	public static final String DIAGNOSIS_ROLE_CODE = "AD";
	
	private DiagnosisConstants() {
	}
}
