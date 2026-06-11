package org.openmrs.module.ihshr.fhir;

import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * Minimal, offline validator to prevent sending invalid resources to SHR. This intentionally does
 * not require a running terminology server.
 */
public class ShrFhirValidator {
	
	private final FhirContext fhirContext;
	
	public ShrFhirValidator() {
		this(FhirContextHolder.R4);
	}
	
	public ShrFhirValidator(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}
	
	public void validateOrThrow(IBaseResource resource) {
		if (!ShrFhirValidationSupport.isAvailable()) {
			System.err.println("[Validation] Skipping FHIR validation for " + resource.fhirType()
			        + " (hapi-fhir-validation classes not on classpath; resources were read from local FHIR2 API).");
			return;
		}
		FhirValidator validator = fhirContext.newValidator();
		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
		
		ValidationSupportChain supportChain = new ValidationSupportChain(new DefaultProfileValidationSupport(fhirContext),
		        new PrePopulatedValidationSupport(fhirContext), new InMemoryTerminologyServerValidationSupport(fhirContext));
		instanceValidator.setValidationSupport(supportChain);
		
		validator.registerValidatorModule(instanceValidator);
		ValidationResult result = validator.validateWithResult(resource);
		
		if (result.isSuccessful()) {
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("FHIR validation failed (").append(resource.fhirType()).append("):");
		result.getMessages().forEach(msg -> sb.append("\n - ").append(msg.getSeverity()).append(": ").append(msg.getMessage()));
		throw new IllegalArgumentException(sb.toString());
	}
}
