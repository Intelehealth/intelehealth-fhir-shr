package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;

/**
 * SHR transaction bundles require referenced Practitioner and Location before Encounter.
 */
public class VisitTransactionBundleEncounterReferencesTest {
	
	private static final String PATIENT_UUID = "3c2a29ff-370c-49fd-9310-09e8fa123347";
	
	@Test
	public void prependedPractitionerAndLocationAppearBeforeEncounter() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-encounter-refs",
		        (bundle, addedCruids, subject, logPrefix) -> {
			        Reference rewritten = new Reference("urn:uuid:ih-shr-patient-" + PATIENT_UUID);
			        ShrCruidPatientSupport.rewriteSubjectReference(subject, rewritten.getReference());
			        return true;
		        });
		
		Practitioner practitioner = new Practitioner();
		practitioner.setId("201e9cca-ecd6-4075-9503-373980430bcd");
		practitioner.addName().setFamily("Smith");
		
		Location location = new Location();
		location.setId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
		location.setName("District clinic");
		
		Encounter encounter = new Encounter();
		encounter.setId("enc-visit-1");
		encounter.getSubject().setReference("Patient/" + PATIENT_UUID);
		encounter.addParticipant().getIndividual().setReference("Practitioner/201e9cca-ecd6-4075-9503-373980430bcd");
		encounter.addLocation().getLocation().setReference("Location/a1b2c3d4-e5f6-7890-abcd-ef1234567890");
		
		builder.addPutResource(encounter, "[Encounter]");
		assertTrue(builder.addPutResource(practitioner, "[Practitioner]", true));
		assertTrue(builder.addPutResource(location, "[Location]", true));
		
		Bundle bundle = builder.build();
		int practitionerIndex = indexOf(bundle, "Practitioner");
		int locationIndex = indexOf(bundle, "Location");
		int encounterIndex = indexOf(bundle, "Encounter");
		
		assertTrue(practitionerIndex >= 0);
		assertTrue(locationIndex >= 0);
		assertTrue(encounterIndex >= 0);
		assertTrue("Practitioner must precede Encounter", practitionerIndex < encounterIndex);
		assertTrue("Location must precede Encounter", locationIndex < encounterIndex);
	}
	
	@Test
	public void addPutResource_acceptsPractitionerAndLocationWithoutPatientSubject() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-supporting",
		        (bundle, addedCruids, subject, logPrefix) -> false);
		
		Practitioner practitioner = new Practitioner();
		practitioner.setId("pract-1");
		
		Location location = new Location();
		location.setId("loc-1");
		
		assertTrue(builder.addPutResource(practitioner, "[Practitioner]"));
		assertTrue(builder.addPutResource(location, "[Location]"));
		assertTrue(builder.hasPutResource("Practitioner", "pract-1"));
		assertTrue(builder.hasPutResource("Location", "loc-1"));
	}
	
	private static int indexOf(Bundle bundle, String resourceType) {
		for (int i = 0; i < bundle.getEntry().size(); i++) {
			if (resourceType.equals(bundle.getEntry().get(i).getResource().fhirType())) {
				return i;
			}
		}
		return -1;
	}
}
