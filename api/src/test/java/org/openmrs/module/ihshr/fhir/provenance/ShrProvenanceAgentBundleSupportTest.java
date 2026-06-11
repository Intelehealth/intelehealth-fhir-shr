package org.openmrs.module.ihshr.fhir.provenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.Test;

public class ShrProvenanceAgentBundleSupportTest {
	
	@Test
	public void ensureAgentResourcesInBundle_shouldAddDeviceOrganizationAndPractitionerPutEntries() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		ShrProvenanceAgentBundleSupport.ensureAgentResourcesInBundle(bundle);
		
		boolean hasDevice = false;
		boolean hasOrg = false;
		boolean hasDoctor = false;
		boolean hasHw = false;
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.getResource() instanceof Device) {
				hasDevice = true;
				assertEquals(Bundle.HTTPVerb.PUT, entry.getRequest().getMethod());
				assertEquals("Device/shr-sync-omod", entry.getRequest().getUrl());
			}
			if (entry.getResource() instanceof Organization) {
				hasOrg = true;
				assertEquals("Organization/intelehealth", entry.getRequest().getUrl());
			}
			if (entry.getResource() instanceof Practitioner) {
				Practitioner practitioner = (Practitioner) entry.getResource();
				if ("unknown-doctor".equals(practitioner.getIdElement().getIdPart())) {
					hasDoctor = true;
					assertEquals("Practitioner/unknown-doctor", entry.getRequest().getUrl());
				}
				if ("unknown-hw".equals(practitioner.getIdElement().getIdPart())) {
					hasHw = true;
					assertEquals("Practitioner/unknown-hw", entry.getRequest().getUrl());
				}
			}
		}
		assertTrue(hasDevice);
		assertTrue(hasOrg);
		assertTrue(hasDoctor);
		assertTrue(hasHw);
	}
	
	@Test
	public void appendProvenanceEntry_shouldIncludeDeviceBeforeProvenance() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		ShrProvenanceBundleSupport.appendProvenanceEntry(bundle, ProvenanceAssertionClass.VITALS, "visit-1",
		    java.util.Collections.singletonList("Observation/obs-1"), new java.util.Date(), new java.util.Date(), null,
		    ShrProvenanceReferences.healthWorkerAuthorReference());
		
		int deviceIndex = -1;
		int provenanceIndex = -1;
		for (int i = 0; i < bundle.getEntry().size(); i++) {
			if (bundle.getEntry().get(i).getResource() instanceof Device) {
				deviceIndex = i;
			}
			if (bundle.getEntry().get(i).getResource() instanceof Provenance) {
				provenanceIndex = i;
			}
		}
		assertTrue(deviceIndex >= 0);
		assertTrue(provenanceIndex >= 0);
		assertTrue(deviceIndex < provenanceIndex);
	}
}
