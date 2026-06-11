package org.openmrs.module.ihshr.fhir;

import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

/**
 * Canonical/profile URLs for IH IG validation. Online validation loads profile JSON from the
 * canonical site, but validation must use the StructureDefinition canonical URLs (not the HTML/JSON
 * page URLs).
 */
public final class ShrIgProfileUrls {
	
	public static final String DEFAULT_IG_CANONICAL = "https://ih-ig.mpower-social.com";
	
	private ShrIgProfileUrls() {
	}
	
	public static String igCanonical() {
		// Allow override via OpenMRS GP: ihshr.ig.canonical
		String configured = IhshrPropertyResolver.resolve("ig.canonical");
		return (configured == null || configured.trim().isEmpty()) ? DEFAULT_IG_CANONICAL : configured.trim();
	}
	
	public static String structureDefinition(String id) {
		return igCanonical() + "/StructureDefinition/" + id;
	}
	
	public static final String IH_ENCOUNTER = structureDefinition("ih-encounter");
	
	public static final String IH_OBSERVATION = structureDefinition("ih-observation");
	
	public static final String IH_MEDICATION_REQUEST = structureDefinition("ih-medication-request");
	
	public static final String IH_SERVICE_REQUEST = structureDefinition("ih-service-request");
	
	public static final String IH_LOCATION = structureDefinition("ih-location");
	
	public static final String IH_PRACTITIONER = structureDefinition("ih-practitioner");
	
	public static final String IH_CONDITION = structureDefinition("ih-condition");
	
	public static final String IH_FAMILY_MEMBER_HISTORY = structureDefinition("ih-family-member-history");
	
	public static final String IH_ALLERGY_INTOLERANCE = structureDefinition("ih-allergy-intolerance");
	
	public static final String IH_MEDICATION_STATEMENT = structureDefinition("ih-medication-statement");
	
	public static final String IH_PHYSICAL_EXAM_OBSERVATION = structureDefinition("ih-physical-exam-observation");
	
	public static final String IH_SHR_PUSH_META = structureDefinition("ih-shr-push-meta");
	
	public static final String IH_TRANSACTION_BUNDLE = structureDefinition("ih-transaction-bundle");
}
