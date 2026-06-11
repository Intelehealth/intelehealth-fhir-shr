package org.openmrs.module.ihshr.utils;

public final class FamilyHistoryConstants {
	
	public static final int FAMILY_HISTORY_CONCEPT_ID = 163211;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String ROLE_CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-RoleCode";
	
	public static final String CONDITION_CODE_SYSTEM = "http://snomed.info/sct";
	
	public static String familyMemberHistoryIdentifier(String obsUuid, String relativeKey) {
		return obsUuid + "::fam-" + relativeKey;
	}
	
	private FamilyHistoryConstants() {
	}
	
}
