package org.openmrs.module.ihshr.fhir;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;

/**
 * Resolves {@link Encounter#getPartOf()} references for visit transaction bundles. OpenMRS FHIR2
 * often points {@code partOf} at {@code Encounter/ visit.uuid} even when no encounter row uses that
 * uuid; the SHR server still requires the target to exist in the bundle.
 */
public final class EncounterPartOfSupport {
	
	private EncounterPartOfSupport() {
	}
	
	public static String parseEncounterReferenceId(Reference reference) {
		if (reference == null || !reference.hasReference()) {
			return null;
		}
		String ref = reference.getReference().trim();
		if (ref.startsWith("Encounter/")) {
			return ref.substring("Encounter/".length());
		}
		int slash = ref.lastIndexOf('/');
		if (slash >= 0 && slash < ref.length() - 1) {
			String tail = ref.substring(slash + 1);
			if (ref.contains("Encounter")) {
				return tail;
			}
		}
		return null;
	}
	
	public static Set<String> collectPartOfParentIds(List<Encounter> encounters) {
		Set<String> parentIds = new LinkedHashSet<>();
		if (encounters == null) {
			return parentIds;
		}
		for (Encounter encounter : encounters) {
			if (encounter == null || !encounter.hasPartOf()) {
				continue;
			}
			String parentId = parseEncounterReferenceId(encounter.getPartOf());
			if (StringUtils.isNotBlank(parentId)) {
				parentIds.add(parentId.trim());
			}
		}
		return parentIds;
	}
	
	public static void stripUnresolvedPartOf(Encounter encounter, Set<String> availableEncounterIds) {
		if (encounter == null || !encounter.hasPartOf() || availableEncounterIds == null) {
			return;
		}
		String parentId = parseEncounterReferenceId(encounter.getPartOf());
		if (parentId != null && !availableEncounterIds.contains(parentId)) {
			encounter.setPartOf(null);
		}
	}
	
	public static Encounter buildVisitParentPlaceholder(String visitUuid, Encounter template) {
		Encounter parent = new Encounter();
		parent.setId(visitUuid);
		parent.setStatus(org.hl7.fhir.r4.model.Encounter.EncounterStatus.UNKNOWN);
		parent.getClass_().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB");
		if (template != null) {
			if (template.hasSubject()) {
				parent.setSubject(template.getSubject().copy());
			}
			if (template.hasPeriod()) {
				parent.setPeriod(template.getPeriod().copy());
			}
			if (template.hasParticipant()) {
				parent.setParticipant(template.getParticipant());
			}
		}
		return parent;
	}
}
