package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklogContext;

/**
 * Shared helpers for structured free-text obs export.
 */
public final class StructuredObsContextSupport {
	
	private StructuredObsContextSupport() {
	}
	
	public static void populateBacklogContext(Observation sourceObs, String obsUuid, Integer conceptId) {
		String encounterUuid = referenceId(sourceObs != null && sourceObs.hasEncounter() ? sourceObs.getEncounter() : null);
		String patientUuid = referenceId(sourceObs != null && sourceObs.hasSubject() ? sourceObs.getSubject() : null);
		UnmappedTermBacklogContext.set(obsUuid, encounterUuid, patientUuid, conceptId);
	}
	
	private static String referenceId(Reference reference) {
		if (reference == null || StringUtils.isBlank(reference.getReference())) {
			return null;
		}
		String ref = reference.getReference().trim();
		int slash = ref.lastIndexOf('/');
		return slash >= 0 ? ref.substring(slash + 1) : ref;
	}
	
}
