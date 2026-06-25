package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;

public class EncounterLocationSupportTest {
	
	@Test
	public void extractLocationReferences_returnsDistinctLocationRefs() {
		Encounter encounter = new Encounter();
		encounter.addLocation().getLocation().setReference("Location/loc-1").setDisplay("Clinic A");
		encounter.addLocation().getLocation().setReference("Location/loc-1");
		encounter.addLocation().getLocation().setReference("Location/loc-2");
		
		assertEquals(2, EncounterLocationSupport.extractLocationReferences(encounter).size());
		assertEquals("Location/loc-1", EncounterLocationSupport.extractLocationReferences(encounter).get(0).getReference());
	}
	
	@Test
	public void extractLocationReferences_ignoresNonLocationRefs() {
		Encounter encounter = new Encounter();
		encounter.addLocation().getLocation().setReference("Organization/org-1");
		
		assertTrue(EncounterLocationSupport.extractLocationReferences(encounter).isEmpty());
	}
	
	@Test
	public void extractLocationReferences_preservesDisplay() {
		Encounter encounter = new Encounter();
		Reference ref = encounter.addLocation().getLocation();
		ref.setReference("Location/loc-9");
		ref.setDisplay("Rural clinic");
		
		Reference extracted = EncounterLocationSupport.extractLocationReferences(encounter).get(0);
		assertNotNull(extracted);
		assertEquals("Rural clinic", extracted.getDisplay());
	}
}
