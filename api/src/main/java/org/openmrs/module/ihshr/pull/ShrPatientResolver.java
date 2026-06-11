package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.service.CommonOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("ihshrShrPatientResolver")
public class ShrPatientResolver {
	
	@Autowired
	@Qualifier("ihshrFhirConfig")
	private FhirConfig fhirConfig;
	
	@Autowired
	@Qualifier("ihshrCommonOperationService")
	private CommonOperationService commonOperationService;
	
	public ShrResolvedPatient resolve(String openmrsPatientUuid) {
		if (StringUtils.isBlank(openmrsPatientUuid)) {
			throw new ShrPullException(ShrPullErrorCode.PATIENT_NOT_FOUND, "OpenMRS patient uuid is required");
		}
		Patient openMrsPatient = Context.getPatientService().getPatientByUuid(openmrsPatientUuid.trim());
		if (openMrsPatient == null) {
			throw new ShrPullException(ShrPullErrorCode.PATIENT_NOT_FOUND, "OpenMRS patient not found: "
			        + openmrsPatientUuid);
		}
		String cruid = commonOperationService.getMPIUsingPatientReference(openmrsPatientUuid.trim());
		if (StringUtils.isBlank(cruid)) {
			throw new ShrPullException(ShrPullErrorCode.PATIENT_NOT_SYNCED,
			        "Patient has no MPI/CRUID identifier; sync from Client Registry first");
		}
		ShrResolvedPatient resolved = new ShrResolvedPatient();
		resolved.setOpenmrsPatientUuid(openmrsPatientUuid.trim());
		resolved.setCruid(cruid.trim());
		resolved.setOpenmrsPatientDisplay(openMrsPatient.getPersonName().getFullName());
		lookupShrPatient(resolved);
		return resolved;
	}
	
	private void lookupShrPatient(ShrResolvedPatient resolved) {
		String url = ShrQueryTranslator.buildPatientResolveUrl(resolved.getCruid());
		Bundle bundle = fhirConfig.searchBundleByUrl(url);
		if (bundle == null || !bundle.hasEntry()) {
			resolved.setShrPatientFound(false);
			return;
		}
		List<org.hl7.fhir.r4.model.Patient> patients = new ArrayList<org.hl7.fhir.r4.model.Patient>();
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.hasResource() && entry.getResource() instanceof org.hl7.fhir.r4.model.Patient) {
				patients.add((org.hl7.fhir.r4.model.Patient) entry.getResource());
			}
		}
		if (patients.isEmpty()) {
			resolved.setShrPatientFound(false);
			return;
		}
		if (patients.size() > 1) {
			throw new ShrPullException(ShrPullErrorCode.SHR_AMBIGUOUS_PATIENT,
			        "Multiple SHR Patient resources found for CRUID " + resolved.getCruid());
		}
		org.hl7.fhir.r4.model.Patient shrPatient = patients.get(0);
		resolved.setShrPatientFound(true);
		resolved.setShrPatientId(shrPatient.getIdElement().getIdPart());
		resolved.setShrPatientDisplay(shrPatient.hasName() ? shrPatient.getNameFirstRep().getNameAsSingleString() : null);
	}
}
