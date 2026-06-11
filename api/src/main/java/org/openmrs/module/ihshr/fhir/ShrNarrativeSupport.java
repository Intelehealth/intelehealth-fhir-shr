package org.openmrs.module.ihshr.fhir;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;

/**
 * Removes auto-generated FHIR {@code DomainResource.text} narratives from pushed resources. Does
 * not affect {@code note.text}, {@code code.text}, {@code name.text}, or other nested text fields.
 */
public final class ShrNarrativeSupport {
	
	private ShrNarrativeSupport() {
	}
	
	public static void stripResourceNarrative(Resource resource) {
		if (resource instanceof DomainResource) {
			((DomainResource) resource).setText(null);
		}
	}
	
	public static void stripBundleNarratives(Bundle bundle) {
		if (bundle == null || !bundle.hasEntry()) {
			return;
		}
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.hasResource()) {
				stripResourceNarrative(entry.getResource());
			}
		}
	}
}
