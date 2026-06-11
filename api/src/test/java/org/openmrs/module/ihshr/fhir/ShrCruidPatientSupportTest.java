package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.CruidConstants;

public class ShrCruidPatientSupportTest {
	
	@Test
	public void addPatientEntryIfAbsent_shouldPostIfNoneExistByCruid() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		Set<String> added = new HashSet<>();
		
		String fullUrl = ShrCruidPatientSupport.addPatientEntryIfAbsent(bundle, added, "CR-2026-000001");
		
		assertEquals("urn:uuid:ih-shr-patient-CR-2026-000001", fullUrl);
		assertEquals(1, bundle.getEntry().size());
		Bundle.BundleEntryComponent entry = bundle.getEntryFirstRep();
		assertEquals(Bundle.HTTPVerb.POST, entry.getRequest().getMethod());
		assertEquals("Patient", entry.getRequest().getUrl());
		assertEquals("identifier=" + CruidConstants.IDENTIFIER_SYSTEM + "|CR-2026-000001",
		    entry.getRequest().getIfNoneExist());
		Patient patient = (Patient) entry.getResource();
		assertEquals(CruidConstants.IDENTIFIER_SYSTEM, patient.getIdentifierFirstRep().getSystem());
		assertEquals("CR-2026-000001", patient.getIdentifierFirstRep().getValue());
	}
	
	@Test
	public void addPatientEntryIfAbsent_shouldDeduplicateSameCruid() {
		Bundle bundle = new Bundle();
		Set<String> added = new HashSet<>();
		ShrCruidPatientSupport.addPatientEntryIfAbsent(bundle, added, "CR-2026-000002");
		ShrCruidPatientSupport.addPatientEntryIfAbsent(bundle, added, "CR-2026-000002");
		assertEquals(1, bundle.getEntry().size());
	}
	
	@Test
	public void extractOpenMrsPatientUuid_shouldParsePatientReference() {
		assertEquals("abc-uuid", ShrCruidPatientSupport.extractOpenMrsPatientUuid("Patient/abc-uuid"));
	}
	
	@Test
	public void rewriteSubjectReference_shouldUseBundleFullUrl() {
		Encounter encounter = new Encounter();
		encounter.getSubject().setReference("Patient/openmrs-uuid");
		ShrCruidPatientSupport.rewriteSubjectReference(encounter.getSubject(),
		    ShrCruidPatientSupport.fullUrlForCruid("CR-2026-000003"));
		assertTrue(encounter.getSubject().getReference().startsWith("urn:uuid:ih-shr-patient-"));
	}
}
