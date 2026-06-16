package org.openmrs.module.ihshr.fhir;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.utils.CruidConstants;

/**
 * Builds SHR shadow {@link Patient} entries keyed by CRUID (doc §2.3, §3.1 step 8) and rewrites
 * clinical-resource subject references to in-bundle {@code urn:uuid:} fullUrls.
 */
public final class ShrCruidPatientSupport {
	
	private static final String PATIENT_FULL_URL_PREFIX = "urn:uuid:ih-shr-patient-";
	
	private ShrCruidPatientSupport() {
	}
	
	public static String fullUrlForCruid(String cruid) {
		return PATIENT_FULL_URL_PREFIX + sanitizeForUrn(cruid);
	}
	
	/** True when the reference already points at an in-bundle shadow Patient fullUrl. */
	public static boolean isInBundlePatientReference(String reference) {
		return StringUtils.isNotBlank(reference) && reference.trim().startsWith(PATIENT_FULL_URL_PREFIX);
	}
	
	public static Patient buildShadowPatient(String cruid) {
		Patient patient = new Patient();
		patient.addIdentifier(new Identifier().setSystem(CruidConstants.IDENTIFIER_SYSTEM).setValue(cruid));
		return patient;
	}
	
	public static Bundle.BundleEntryComponent buildPatientIfNoneExistEntry(String cruid) {
		String fullUrl = fullUrlForCruid(cruid);
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		entry.setFullUrl(fullUrl);
		Patient patient = buildShadowPatient(cruid);
		ShrPushMetaApplicator.applyPushMeta(patient);
		entry.setResource(patient);
		entry.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient")
		        .setIfNoneExist("identifier=" + CruidConstants.IDENTIFIER_SYSTEM + "|" + cruid);
		return entry;
	}
	
	/**
	 * Inserts a Patient entry when {@code cruid} is not already present in {@code addedCruids}.
	 * 
	 * @return fullUrl for the shadow patient, or {@code null} when {@code cruid} is blank
	 */
	public static String addPatientEntryIfAbsent(Bundle transactionBundle, Set<String> addedCruids, String cruid) {
		if (StringUtils.isBlank(cruid)) {
			return null;
		}
		String trimmed = cruid.trim();
		if (addedCruids.contains(trimmed)) {
			return fullUrlForCruid(trimmed);
		}
		transactionBundle.getEntry().add(0, buildPatientIfNoneExistEntry(trimmed));
		addedCruids.add(trimmed);
		return fullUrlForCruid(trimmed);
	}
	
	public static void rewriteSubjectReference(Reference subject, String patientFullUrl) {
		if (subject == null || StringUtils.isBlank(patientFullUrl)) {
			return;
		}
		subject.setReference(patientFullUrl);
	}
	
	/**
	 * OpenMRS person uuid from {@code Patient/ uuid} or absolute URL ending with that segment.
	 */
	public static String extractOpenMrsPatientUuid(String reference) {
		if (StringUtils.isBlank(reference)) {
			return null;
		}
		String trimmed = reference.trim();
		if (trimmed.startsWith("urn:")) {
			return null;
		}
		int slash = trimmed.lastIndexOf('/');
		String idPart = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
		return StringUtils.isBlank(idPart) ? null : idPart;
	}
	
	public static Reference extractSubjectReference(Resource resource) {
		if (resource instanceof Observation) {
			return ((Observation) resource).getSubject();
		}
		if (resource instanceof Condition) {
			return ((Condition) resource).getSubject();
		}
		if (resource instanceof Encounter) {
			return ((Encounter) resource).getSubject();
		}
		if (resource instanceof MedicationRequest) {
			return ((MedicationRequest) resource).getSubject();
		}
		if (resource instanceof ServiceRequest) {
			return ((ServiceRequest) resource).getSubject();
		}
		if (resource instanceof FamilyMemberHistory) {
			return ((FamilyMemberHistory) resource).getPatient();
		}
		if (resource instanceof AllergyIntolerance) {
			return ((AllergyIntolerance) resource).getPatient();
		}
		if (resource instanceof MedicationStatement) {
			return ((MedicationStatement) resource).getSubject();
		}
		if (resource instanceof DocumentReference) {
			return ((DocumentReference) resource).getSubject();
		}
		return null;
	}
	
	static String sanitizeForUrn(String cruid) {
		return cruid.trim().replaceAll("[^A-Za-z0-9._-]", "-");
	}
	
	/** For tests: whether bundle already contains a Patient entry for this CRUID. */
	static boolean bundleContainsCruid(Bundle bundle, String cruid) {
		if (bundle == null || StringUtils.isBlank(cruid)) {
			return false;
		}
		String expectedFullUrl = fullUrlForCruid(cruid.trim());
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (expectedFullUrl.equals(entry.getFullUrl()) && entry.getResource() instanceof Patient) {
				return true;
			}
		}
		return false;
	}
	
	static Set<String> collectCruidsInBundle(Bundle bundle) {
		Set<String> out = new HashSet<>();
		if (bundle == null) {
			return out;
		}
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (!(entry.getResource() instanceof Patient)) {
				continue;
			}
			for (Identifier id : ((Patient) entry.getResource()).getIdentifier()) {
				if (CruidConstants.IDENTIFIER_SYSTEM.equals(id.getSystem()) && id.hasValue()) {
					out.add(id.getValue());
				}
			}
		}
		return out;
	}
}
