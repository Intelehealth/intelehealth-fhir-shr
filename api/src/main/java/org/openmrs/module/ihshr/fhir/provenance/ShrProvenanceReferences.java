package org.openmrs.module.ihshr.fhir.provenance;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

/**
 * Resolves SHR-side references for Provenance agents (doc §12 — Organization, Device,
 * Practitioner).
 */
public final class ShrProvenanceReferences {
	
	private static final String PROP_ORG = "shr.provenance.organization.reference";
	
	private static final String PROP_DEVICE = "shr.provenance.device.reference";
	
	private static final String PROP_DOCTOR = "shr.provenance.practitioner.doctor.reference";
	
	private static final String PROP_HW = "shr.provenance.practitioner.hw.reference";
	
	private ShrProvenanceReferences() {
	}
	
	public static Reference organizationReference() {
		return referenceFromProperty(PROP_ORG, "Organization/intelehealth");
	}
	
	public static Reference transmitterDeviceReference() {
		return referenceFromProperty(PROP_DEVICE, "Device/shr-sync-omod");
	}
	
	public static Reference doctorAuthorReference() {
		return referenceFromProperty(PROP_DOCTOR, "Practitioner/unknown-doctor");
	}
	
	public static Reference healthWorkerAuthorReference() {
		return referenceFromProperty(PROP_HW, "Practitioner/unknown-hw");
	}
	
	public static Reference authorForAssertionClass(ProvenanceAssertionClass assertionClass) {
		switch (assertionClass) {
			case VISIT_COMPLETION:
			case DIAGNOSES:
			case PRESCRIPTIONS:
			case ORDERS_REFERRALS:
				return doctorAuthorReference();
			default:
				return healthWorkerAuthorReference();
		}
	}
	
	private static Reference referenceFromProperty(String key, String defaultReference) {
		String configured = StringUtils.trimToNull(IhshrPropertyResolver.resolve(key));
		Reference ref = new Reference();
		ref.setReference(configured != null ? configured : defaultReference);
		return ref;
	}
}
