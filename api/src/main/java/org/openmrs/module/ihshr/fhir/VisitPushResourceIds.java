package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;

/**
 * Stable client-assigned ids for idempotent {@code PUT} entries in a visit transaction bundle.
 */
public final class VisitPushResourceIds {
	
	private static final int FHIR_ID_MAX_LENGTH = 64;
	
	private VisitPushResourceIds() {
	}
	
	public static String resolvePutResourceId(Resource resource) {
		String rawId = resolveRawPutResourceId(resource);
		return toFhirResourceId(rawId);
	}
	
	static String resolveRawPutResourceId(Resource resource) {
		if (resource == null) {
			return null;
		}
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
		} else if (resource instanceof FamilyMemberHistory) {
			FamilyMemberHistory history = (FamilyMemberHistory) resource;
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
		} else if (resource instanceof ServiceRequest) {
			ServiceRequest serviceRequest = (ServiceRequest) resource;
			if (serviceRequest.hasIdentifier()) {
				return serviceRequest.getIdentifierFirstRep().getValue();
			}
		} else if (resource instanceof Binary) {
			Binary binary = (Binary) resource;
			if (binary.hasIdElement() && binary.getIdElement().hasIdPart()) {
				return binary.getIdElement().getIdPart();
			}
		}
		return null;
	}
	
	static String toFhirResourceId(String rawId) {
		if (StringUtils.isBlank(rawId)) {
			return null;
		}
		String trimmed = rawId.trim();
		String sanitized = trimmed.replaceAll("[^A-Za-z0-9\\-.]", "-");
		sanitized = sanitized.replaceAll("-{2,}", "-");
		sanitized = sanitized.replaceAll("^[\\-.]+", "");
		sanitized = sanitized.replaceAll("[\\-.]+$", "");
		if (StringUtils.isBlank(sanitized)) {
			return compactHashId(trimmed);
		}
		if (sanitized.length() <= FHIR_ID_MAX_LENGTH) {
			return sanitized;
		}
		String hash = Integer.toHexString(trimmed.hashCode());
		int keep = FHIR_ID_MAX_LENGTH - hash.length() - 1;
		if (keep < 1) {
			return hash.substring(0, Math.min(hash.length(), FHIR_ID_MAX_LENGTH));
		}
		return sanitized.substring(0, keep) + "-" + hash;
	}
	
	private static String compactHashId(String value) {
		String hash = Integer.toHexString(value.hashCode());
		return hash.length() <= FHIR_ID_MAX_LENGTH ? hash : hash.substring(0, FHIR_ID_MAX_LENGTH);
	}
	
	public static String entryKey(Bundle.BundleEntryComponent entry) {
		if (entry != null && entry.hasRequest() && entry.getRequest().hasUrl()) {
			return entry.getRequest().getMethod().toCode() + " " + entry.getRequest().getUrl();
		}
		if (entry != null && entry.hasResource()) {
			Resource resource = entry.getResource();
			String id = resolvePutResourceId(resource);
			if (id != null) {
				return resource.fhirType() + "/" + id;
			}
		}
		return null;
	}
	
	public static String putUrl(Resource resource) {
		if (resource == null) {
			return null;
		}
		if (resource instanceof DocumentReference) {
			DocumentReference docRef = (DocumentReference) resource;
			if (docRef.getIdElement() != null && docRef.getIdElement().hasIdPart()) {
				return "DocumentReference/" + docRef.getIdElement().getIdPart();
			}
			if (docRef.hasIdentifier()) {
				String rawId = resolveRawPutResourceId(resource);
				if (StringUtils.isNotBlank(rawId)) {
					return "DocumentReference?identifier=" + docRef.getIdentifierFirstRep().getSystem() + "|" + rawId;
				}
			}
		}
		String id = resolvePutResourceId(resource);
		if (StringUtils.isBlank(id)) {
			return null;
		}
		return resource.fhirType() + "/" + id;
	}
}
