package org.openmrs.module.ihshr.utils;

public final class ReferralConstants {
	
	public static final int REFERRAL_CONCEPT_ID = 165238;
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:ServiceRequest";
	
	public static final String SNOMED_SYSTEM = "http://snomed.info/sct";
	
	/**
	 * Used to distinguish referrals from lab/advice ServiceRequests in pull queries.
	 */
	public static final String SERVICE_REQUEST_TYPE_SYSTEM = "urn:intelehealth:service-request-type";
	
	public static final String SERVICE_REQUEST_TYPE_REFERRAL = "referral";
	
	public static final String CATEGORY_SYSTEM = "urn:intelehealth:referral-category";
	
	public static String serviceRequestIdentifier(String obsUuid, int index) {
		return obsUuid + "::ref-" + index;
	}
	
	private ReferralConstants() {
	}
	
}
