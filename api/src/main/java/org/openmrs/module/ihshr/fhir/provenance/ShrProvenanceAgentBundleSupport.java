package org.openmrs.module.ihshr.fhir.provenance;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Ensures SHR-side {@link Organization}, {@link Device}, and {@link Practitioner} resources
 * referenced by Provenance agents exist in the same transaction bundle (HAPI rejects unresolved
 * {@code agent.who} references).
 */
public final class ShrProvenanceAgentBundleSupport {
	
	private ShrProvenanceAgentBundleSupport() {
	}
	
	public static void ensureAgentResourcesInBundle(Bundle transactionBundle) {
		if (transactionBundle == null) {
			return;
		}
		String orgPutUrl = putUrlFromReference(ShrProvenanceReferences.organizationReference().getReference(),
		    "Organization/" + ProvenanceConstants.ORGANIZATION_IDENTIFIER_VALUE);
		String devicePutUrl = putUrlFromReference(ShrProvenanceReferences.transmitterDeviceReference().getReference(),
		    "Device/" + ProvenanceConstants.DEVICE_IDENTIFIER_VALUE);
		String doctorPutUrl = putUrlFromReference(ShrProvenanceReferences.doctorAuthorReference().getReference(),
		    "Practitioner/" + ProvenanceConstants.PRACTITIONER_DOCTOR_IDENTIFIER_VALUE);
		String hwPutUrl = putUrlFromReference(ShrProvenanceReferences.healthWorkerAuthorReference().getReference(),
		    "Practitioner/" + ProvenanceConstants.PRACTITIONER_HW_IDENTIFIER_VALUE);
		ensurePutEntry(transactionBundle, buildOrganization(orgPutUrl), orgPutUrl);
		ensurePutEntry(transactionBundle, buildDevice(devicePutUrl), devicePutUrl);
		ensurePutEntry(transactionBundle, buildPractitionerPlaceholder(doctorPutUrl, "Unknown doctor"), doctorPutUrl);
		ensurePutEntry(transactionBundle, buildPractitionerPlaceholder(hwPutUrl, "Unknown health worker"), hwPutUrl);
	}
	
	/**
	 * Ensures a {@link Practitioner} exists for a Provenance author reference (e.g. encounter
	 * participant).
	 */
	public static void ensurePractitionerAuthorInBundle(Bundle transactionBundle, Reference authorWho) {
		if (transactionBundle == null || authorWho == null || !authorWho.hasReference()) {
			return;
		}
		String reference = StringUtils.trimToNull(authorWho.getReference());
		if (reference == null || !reference.startsWith("Practitioner/")) {
			return;
		}
		String putUrl = reference;
		String display = StringUtils.trimToNull(authorWho.getDisplay());
		ensurePutEntry(transactionBundle, buildPractitionerPlaceholder(putUrl, display != null ? display : "Practitioner"),
		    putUrl);
	}
	
	private static Organization buildOrganization(String putUrl) {
		String id = idFromPutUrl(putUrl);
		Organization organization = new Organization();
		organization.setId(id);
		organization.setName("Intelehealth");
		organization.addIdentifier(new Identifier().setSystem(ProvenanceConstants.ORGANIZATION_IDENTIFIER_SYSTEM).setValue(
		    ProvenanceConstants.ORGANIZATION_IDENTIFIER_VALUE));
		return organization;
	}
	
	private static Device buildDevice(String putUrl) {
		String id = idFromPutUrl(putUrl);
		Device device = new Device();
		device.setId(id);
		device.addIdentifier(new Identifier().setSystem(ProvenanceConstants.DEVICE_IDENTIFIER_SYSTEM).setValue(
		    ProvenanceConstants.DEVICE_IDENTIFIER_VALUE));
		return device;
	}
	
	private static Practitioner buildPractitionerPlaceholder(String putUrl, String displayName) {
		String id = idFromPutUrl(putUrl);
		Practitioner practitioner = new Practitioner();
		practitioner.setId(id);
		practitioner.addIdentifier(new Identifier().setSystem(ProvenanceConstants.PRACTITIONER_IDENTIFIER_SYSTEM).setValue(
		    id));
		HumanName name = new HumanName();
		name.setText(displayName);
		practitioner.addName(name);
		return practitioner;
	}
	
	private static void ensurePutEntry(Bundle transactionBundle, Resource resource, String putUrl) {
		if (resource == null || StringUtils.isBlank(putUrl)) {
			return;
		}
		if (bundleContainsPutUrl(transactionBundle, putUrl)) {
			return;
		}
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		entry.setResource(resource);
		entry.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl(putUrl);
		int insertAt = 0;
		while (insertAt < transactionBundle.getEntry().size()) {
			Resource existing = transactionBundle.getEntry().get(insertAt).getResource();
			if (existing instanceof Patient) {
				insertAt++;
				continue;
			}
			break;
		}
		transactionBundle.getEntry().add(insertAt, entry);
	}
	
	private static boolean bundleContainsPutUrl(Bundle transactionBundle, String putUrl) {
		for (Bundle.BundleEntryComponent entry : transactionBundle.getEntry()) {
			if (entry.hasRequest() && putUrl.equals(entry.getRequest().getUrl())) {
				return true;
			}
		}
		return false;
	}
	
	private static String putUrlFromReference(String reference, String defaultPutUrl) {
		if (StringUtils.isBlank(reference)) {
			return defaultPutUrl;
		}
		String trimmed = reference.trim();
		if (trimmed.contains("/")) {
			return trimmed;
		}
		return defaultPutUrl;
	}
	
	private static String idFromPutUrl(String putUrl) {
		int slash = putUrl.lastIndexOf('/');
		return slash >= 0 ? putUrl.substring(slash + 1) : putUrl;
	}
}
