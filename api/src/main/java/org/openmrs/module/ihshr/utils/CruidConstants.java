package org.openmrs.module.ihshr.utils;

/**
 * Client Registry UID (CRUID) — cross-cutting patient identifier for SHR push/pull (doc §2.3).
 * Facility OpenMRS stores the value on the {@code MPI} patient identifier type.
 */
public final class CruidConstants {
	
	public static final String IDENTIFIER_SYSTEM = "urn:intelehealth:cruid";
	
	private CruidConstants() {
	}
}
