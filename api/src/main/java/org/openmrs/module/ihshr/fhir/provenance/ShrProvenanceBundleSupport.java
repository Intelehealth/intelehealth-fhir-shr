package org.openmrs.module.ihshr.fhir.provenance;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Appends a Provenance entry to an existing transaction bundle (after clinical resources).
 */
public final class ShrProvenanceBundleSupport {
	
	private ShrProvenanceBundleSupport() {
	}
	
	public static void appendProvenanceEntry(Bundle transactionBundle, ProvenanceAssertionClass assertionClass,
	        String visitUuid, List<String> targetReferences, Date occurred, Date recorded, String sourceObsUuid,
	        Reference authorWho) {
		if (transactionBundle == null || targetReferences == null || targetReferences.isEmpty()) {
			return;
		}
		ShrProvenanceAgentBundleSupport.ensureAgentResourcesInBundle(transactionBundle);
		ShrProvenanceAgentBundleSupport.ensurePractitionerAuthorInBundle(transactionBundle, authorWho);
		Provenance provenance = ShrProvenanceBuilder.build(assertionClass, visitUuid, targetReferences, occurred, recorded,
		    sourceObsUuid, authorWho);
		Bundle.BundleEntryComponent entry = transactionBundle.addEntry();
		entry.setResource(provenance);
		String provId = ShrProvenanceBuilder.toProvenanceResourceId(visitUuid, assertionClass);
		entry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Provenance/" + provId);
	}
	
	public static List<String> targetsFromBundleClinicalEntries(Bundle transactionBundle) {
		List<String> targets = new ArrayList<>();
		if (transactionBundle == null || !transactionBundle.hasEntry()) {
			return targets;
		}
		for (Bundle.BundleEntryComponent bundleEntry : transactionBundle.getEntry()) {
			if (!bundleEntry.hasResource()) {
				continue;
			}
			Resource resource = bundleEntry.getResource();
			if (resource instanceof Provenance || resource instanceof org.hl7.fhir.r4.model.Patient) {
				continue;
			}
			String type = resource.fhirType();
			String id = resolveResourceId(resource, bundleEntry);
			if (id != null) {
				targets.add(ShrProvenanceBuilder.canonicalTargetReference(type, id));
			}
		}
		return targets;
	}
	
	private static String resolveResourceId(Resource resource, Bundle.BundleEntryComponent entry) {
		if (resource.getIdElement() != null && resource.getIdElement().hasIdPart()) {
			return resource.getIdElement().getIdPart();
		}
		if (resource instanceof Observation) {
			Observation observation = (Observation) resource;
			if (observation.hasIdentifier()) {
				return observation.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof Condition) {
			Condition condition = (Condition) resource;
			if (condition.hasIdentifier()) {
				return condition.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof org.hl7.fhir.r4.model.FamilyMemberHistory) {
			org.hl7.fhir.r4.model.FamilyMemberHistory history = (org.hl7.fhir.r4.model.FamilyMemberHistory) resource;
			if (history.hasIdentifier()) {
				return history.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof AllergyIntolerance) {
			AllergyIntolerance allergy = (AllergyIntolerance) resource;
			if (allergy.hasIdentifier()) {
				return allergy.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof MedicationStatement) {
			MedicationStatement statement = (MedicationStatement) resource;
			if (statement.hasIdentifier()) {
				return statement.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof DocumentReference) {
			DocumentReference docRef = (DocumentReference) resource;
			if (docRef.hasIdentifier()) {
				return docRef.getIdentifierFirstRep().getValue();
			}
		}
		if (entry.getFullUrl() != null && entry.getFullUrl().startsWith("urn:uuid:")) {
			return entry.getFullUrl();
		}
		return null;
	}
}
