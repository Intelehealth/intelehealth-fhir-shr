package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;

/**
 * Resolves {@link Encounter#getParticipant()} individual references for SHR Provenance agents.
 */
public final class EncounterPractitionerSupport {
	
	private static final String OPENMRS_ENCOUNTER_TYPE_SYSTEM = "http://fhir.openmrs.org/code-system/encounter-type";
	
	private EncounterPractitionerSupport() {
	}
	
	public static Reference extractParticipantIndividualReference(Encounter encounter) {
		if (encounter == null || !encounter.hasParticipant()) {
			return null;
		}
		for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
			if (participant == null || !participant.hasIndividual()) {
				continue;
			}
			Reference individual = participant.getIndividual();
			if (individual != null && individual.hasReference()) {
				String ref = StringUtils.trimToNull(individual.getReference());
				if (ref != null && ref.startsWith("Practitioner/")) {
					Reference copy = new Reference();
					copy.setReference(ref);
					if (individual.hasType()) {
						copy.setType(individual.getType());
					}
					if (individual.hasDisplay()) {
						copy.setDisplay(individual.getDisplay());
					}
					return copy;
				}
			}
		}
		return null;
	}
	
	public static boolean isVitalsEncounter(Encounter encounter) {
		if (encounter == null || !encounter.hasType()) {
			return false;
		}
		return encounter.getType().stream().anyMatch(type -> type != null && type.hasCoding()
		        && type.getCoding().stream().anyMatch(coding -> coding != null && ("Vitals".equalsIgnoreCase(coding.getDisplay())
		                || (OPENMRS_ENCOUNTER_TYPE_SYSTEM.equals(coding.getSystem())
		                        && "67a71486-1a54-468f-ac3e-7091a9a79584".equalsIgnoreCase(coding.getCode())))));
	}
	
	public static boolean isVisitCompleteEncounter(Encounter encounter) {
		if (encounter == null || !encounter.hasType()) {
			return false;
		}
		return encounter.getType().stream().anyMatch(type -> type != null && type.hasCoding()
		        && type.getCoding().stream()
		                .anyMatch(coding -> coding != null && ("Visit Complete".equalsIgnoreCase(coding.getDisplay())
		                        || (OPENMRS_ENCOUNTER_TYPE_SYSTEM.equals(coding.getSystem())
		                                && "bd1fbfaa-f5fb-4ebd-b75c-564506fc309e".equalsIgnoreCase(coding.getCode())))));
	}
}
