package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;

/**
 * Resolves {@link Encounter#getLocation()} references for SHR visit transaction bundles.
 */
public final class EncounterLocationSupport {
	
	private EncounterLocationSupport() {
	}
	
	public static List<Reference> extractLocationReferences(Encounter encounter) {
		List<Reference> locations = new ArrayList<>();
		if (encounter == null || !encounter.hasLocation()) {
			return locations;
		}
		Set<String> seen = new LinkedHashSet<>();
		for (Encounter.EncounterLocationComponent locationComponent : encounter.getLocation()) {
			if (locationComponent == null || !locationComponent.hasLocation()) {
				continue;
			}
			Reference location = locationComponent.getLocation();
			if (location == null || !location.hasReference()) {
				continue;
			}
			String ref = StringUtils.trimToNull(location.getReference());
			if (ref == null || !ref.startsWith("Location/") || !seen.add(ref)) {
				continue;
			}
			Reference copy = new Reference();
			copy.setReference(ref);
			if (location.hasType()) {
				copy.setType(location.getType());
			}
			if (location.hasDisplay()) {
				copy.setDisplay(location.getDisplay());
			}
			locations.add(copy);
		}
		return locations;
	}
}
