package org.openmrs.module.ihshr.fhir.provenance;

/**
 * Doc §12 — Provenance identifiers, agents, and terminology bindings.
 */
public final class ProvenanceConstants {
	
	public static final String VISIT_IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:visit";
	
	public static final String OPENMRS_OBS_IDENTIFIER_SYSTEM = "urn:intelehealth:openmrs:obs";
	
	public static final String ORGANIZATION_IDENTIFIER_SYSTEM = "urn:intelehealth:org";
	
	public static final String ORGANIZATION_IDENTIFIER_VALUE = "intelehealth";
	
	public static final String DEVICE_IDENTIFIER_SYSTEM = "urn:intelehealth:device";
	
	public static final String DEVICE_IDENTIFIER_VALUE = "shr-sync-omod";
	
	public static final String PRACTITIONER_IDENTIFIER_SYSTEM = "urn:intelehealth:practitioner";
	
	public static final String PRACTITIONER_DOCTOR_IDENTIFIER_VALUE = "unknown-doctor";
	
	public static final String PRACTITIONER_HW_IDENTIFIER_VALUE = "unknown-hw";
	
	public static final String DATA_OPERATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-DataOperation";
	
	public static final String DATA_OPERATION_CREATE = "CREATE";
	
	public static final String PARTICIPANT_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/provenance-participant-type";
	
	public static final String PARTICIPANT_AUTHOR = "author";
	
	public static final String PARTICIPANT_TRANSMITTER = "transmitter";
	
	public static final String ENTITY_ROLE_SOURCE = "source";
	
	/**
	 * Custom extension used because this HAPI R4 Provenance model does not expose a native
	 * identifier field.
	 */
	public static final String PROVENANCE_IDENTIFIER_EXTENSION_URL = "https://intelehealth.org/fhir/StructureDefinition/provenance-identifier";
	
	private ProvenanceConstants() {
	}
}
