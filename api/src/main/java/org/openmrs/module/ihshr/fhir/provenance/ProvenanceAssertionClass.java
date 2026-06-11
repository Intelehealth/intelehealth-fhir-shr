package org.openmrs.module.ihshr.fhir.provenance;

/**
 * Doc §12.1 — one {@link org.hl7.fhir.r4.model.Provenance} per assertion class per visit.
 */
public enum ProvenanceAssertionClass {
	
	VISIT_COMPLETION("prov-visit-completion"), VITALS("prov-vitals"), CHIEF_COMPLAINT("prov-chief-complaint"), PHYSICAL_EXAMINATION(
	        "prov-physical-examination"), FAMILY_HISTORY("prov-family-history"), PATIENT_MEDICAL_HISTORY(
	        "prov-medical-history"), DIAGNOSES("prov-diagnosis"), PRESCRIPTIONS("prov-prescriptions"), ORDERS_REFERRALS(
	        "prov-orders"), DOCUMENTS_IMAGES("prov-documents");
	
	private final String identifierSuffix;
	
	ProvenanceAssertionClass(String identifierSuffix) {
		this.identifierSuffix = identifierSuffix;
	}
	
	public String getIdentifierSuffix() {
		return identifierSuffix;
	}
	
	public String provenanceIdentifierValue(String visitUuid) {
		return visitUuid + "::" + identifierSuffix;
	}
}
