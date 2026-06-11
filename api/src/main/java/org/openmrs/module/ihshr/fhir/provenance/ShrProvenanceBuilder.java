package org.openmrs.module.ihshr.fhir.provenance;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Provenance.ProvenanceAgentComponent;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityComponent;
import org.openmrs.module.ihshr.fhir.ShrPushMetaApplicator;

/**
 * Builds doc §12 Provenance resources (per assertion class, grouped targets).
 */
public final class ShrProvenanceBuilder {
	
	private ShrProvenanceBuilder() {
	}
	
	public static Provenance build(ProvenanceAssertionClass assertionClass, String visitUuid, List<String> targetReferences,
	        Date occurred, Date recorded, String sourceObsUuid, Reference authorWho) {
		if (StringUtils.isBlank(visitUuid)) {
			throw new IllegalArgumentException("visitUuid is required for Provenance");
		}
		if (targetReferences == null || targetReferences.isEmpty()) {
			throw new IllegalArgumentException("At least one Provenance target is required");
		}
		
		Provenance provenance = new Provenance();
		String trimmedVisitUuid = visitUuid.trim();
		provenance.setId(toProvenanceResourceId(trimmedVisitUuid, assertionClass));
		provenance
		        .addExtension()
		        .setUrl(ProvenanceConstants.PROVENANCE_IDENTIFIER_EXTENSION_URL)
		        .setValue(
		            new Identifier().setSystem(ProvenanceConstants.VISIT_IDENTIFIER_SYSTEM).setValue(
		                assertionClass.provenanceIdentifierValue(trimmedVisitUuid)));
		
		for (String target : targetReferences) {
			if (StringUtils.isNotBlank(target)) {
				provenance.addTarget(new Reference(target.trim()));
			}
		}
		
		Date occurredAt = occurred != null ? occurred : recorded;
		Date recordedAt = recorded != null ? recorded : occurredAt;
		if (occurredAt != null) {
			provenance.setOccurred(new org.hl7.fhir.r4.model.DateTimeType(occurredAt));
		}
		if (recordedAt != null) {
			provenance.setRecorded(recordedAt);
		}
		
		CodeableConcept activity = new CodeableConcept();
		activity.addCoding(new Coding().setSystem(ProvenanceConstants.DATA_OPERATION_SYSTEM)
		        .setCode(ProvenanceConstants.DATA_OPERATION_CREATE).setDisplay("create"));
		provenance.setActivity(activity);
		
		Reference author = authorWho != null ? authorWho : ShrProvenanceReferences.authorForAssertionClass(assertionClass);
		provenance.addAgent(authorAgent(author));
		provenance.addAgent(transmitterAgent());
		
		if (StringUtils.isNotBlank(sourceObsUuid)) {
			ProvenanceEntityComponent entity = new ProvenanceEntityComponent();
			entity.setRole(Provenance.ProvenanceEntityRole.SOURCE);
			Identifier sourceId = new Identifier().setSystem(ProvenanceConstants.OPENMRS_OBS_IDENTIFIER_SYSTEM).setValue(
			    sourceObsUuid.trim());
			entity.setWhat(new Reference().setIdentifier(sourceId));
			provenance.addEntity(entity);
		}
		
		ShrPushMetaApplicator.applyPushMeta(provenance);
		return provenance;
	}
	
	private static ProvenanceAgentComponent authorAgent(Reference who) {
		ProvenanceAgentComponent agent = new ProvenanceAgentComponent();
		agent.setType(participantType(ProvenanceConstants.PARTICIPANT_AUTHOR, "Author"));
		agent.setWho(who);
		agent.setOnBehalfOf(ShrProvenanceReferences.organizationReference());
		return agent;
	}
	
	private static ProvenanceAgentComponent transmitterAgent() {
		ProvenanceAgentComponent agent = new ProvenanceAgentComponent();
		agent.setType(participantType(ProvenanceConstants.PARTICIPANT_TRANSMITTER, "Transmitter"));
		agent.setWho(ShrProvenanceReferences.transmitterDeviceReference());
		return agent;
	}
	
	private static CodeableConcept participantType(String code, String display) {
		CodeableConcept type = new CodeableConcept();
		type.addCoding(new Coding().setSystem(ProvenanceConstants.PARTICIPANT_TYPE_SYSTEM).setCode(code).setDisplay(display));
		return type;
	}
	
	public static String canonicalTargetReference(String resourceType, String resourceId) {
		if (StringUtils.isAnyBlank(resourceType, resourceId)) {
			return null;
		}
		return resourceType + "/" + resourceId.trim();
	}
	
	public static List<String> copyTargets(List<String> targets) {
		return targets == null ? new ArrayList<>() : new ArrayList<>(targets);
	}
	
	/** Stable SHR resource id / conditional PUT key for a visit assertion class. */
	public static String toProvenanceResourceId(String visitUuid, ProvenanceAssertionClass assertionClass) {
		return assertionClass.provenanceIdentifierValue(visitUuid.trim()).replace("::", "--");
	}
}
