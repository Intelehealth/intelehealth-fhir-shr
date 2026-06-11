package org.openmrs.module.ihshr.fhir;

import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;

/**
 * Injects shadow {@link org.hl7.fhir.r4.model.Patient} (CRUID) entries into a visit push bundle.
 */
@FunctionalInterface
public interface VisitPushCruidSupport {
	
	boolean ensurePatient(Bundle transactionBundle, Set<String> addedCruids, Reference subject, String logPrefix);
}
