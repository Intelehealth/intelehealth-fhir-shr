package org.openmrs.module.ihshr.utils;

/**
 * IHSHR configuration accessors. Each call resolves the latest value from OpenMRS global properties
 * ({@code ihshr.<key>}) or classpath defaults.
 */
public abstract class IHConstant {
	
	protected String getImportLocation() {
		return IhshrPropertyResolver.resolve("resource.location.import");
	}
	
	protected String getExportLocation() {
		return IhshrPropertyResolver.resolve("resource.location.export");
	}
	
	protected String getExportPatient() {
		return IhshrPropertyResolver.resolve("resource.patient.export");
	}
	
	protected String getExportPractitioner() {
		return IhshrPropertyResolver.resolve("resource.practitioner.export");
	}
	
	protected String getExportEncounter() {
		return IhshrPropertyResolver.resolve("resource.encounter.export");
	}
	
	protected String getExportObservation() {
		return IhshrPropertyResolver.resolve("resource.observation.export");
	}
	
	protected String getExportMedication() {
		return IhshrPropertyResolver.resolve("resource.medication.export");
	}
	
	protected String getExportMedicationRequest() {
		return IhshrPropertyResolver.resolve("resource.medication.request.export");
	}
	
	protected String getExportServiceRequest() {
		return IhshrPropertyResolver.resolve("resource.service.request.export");
	}
	
	protected String getExportDiagnosticReport() {
		return IhshrPropertyResolver.resolve("resource.diagnostic.report.export");
	}
	
	protected String getLocalOpenmrsURL() {
		return IhshrPropertyResolver.resolve("local.openmrs.url", "local.openmrs.openhim.url");
	}
	
	protected String getLocalOpenmrsAuthentication() {
		return IhshrPropertyResolver.resolve("local.openmrs.clientid.password.basic.auth",
		    "local.openmrs.openhim.clientid.password.basic.auth");
	}
	
	protected String getOpencrOpenhimURL() {
		return getShrUrl();
	}
	
	protected String getOpencrOpenhimAuthentication() {
		return getShrCredential();
	}
	
	protected String getGofrOpenhimURL() {
		return IhshrPropertyResolver.resolve("gofr.openhim.url");
	}
	
	protected String getGofrOpenhimAuthentication() {
		return IhshrPropertyResolver.resolve("gofr.openhim.clientid.password.basic.auth");
	}
	
	protected String getShrUrl() {
		return ShrFhirUrlSupport.resolveShrFhirBaseUrl();
	}
	
	protected String getShrCredential() {
		return IhshrPropertyResolver.resolve("opencr.shr.credential");
	}
}
