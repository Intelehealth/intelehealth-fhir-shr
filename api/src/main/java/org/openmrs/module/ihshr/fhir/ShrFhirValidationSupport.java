package org.openmrs.module.ihshr.fhir;

/**
 * Detects whether HAPI FHIR instance validation ({@code hapi-fhir-validation}) is available at
 * runtime. IHSHR compiles against HAPI 5.7 but OpenMRS typically loads HAPI from the fhir2 module,
 * which may not include {@code org.hl7.fhir.common.hapi.validation.*} classes.
 */
public final class ShrFhirValidationSupport {
	
	private static final boolean AVAILABLE = detectAvailable();
	
	private static final String VALIDATOR_CLASS = "org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator";
	
	private ShrFhirValidationSupport() {
	}
	
	public static boolean isAvailable() {
		return AVAILABLE;
	}
	
	private static boolean detectAvailable() {
		try {
			Class.forName(VALIDATOR_CLASS, false, ShrFhirValidationSupport.class.getClassLoader());
			return true;
		}
		catch (Throwable ignored) {
			return false;
		}
	}
}
