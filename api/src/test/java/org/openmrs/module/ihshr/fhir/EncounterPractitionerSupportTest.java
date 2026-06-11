package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass;

public class EncounterPractitionerSupportTest {
	
	@Test
	public void extractParticipantIndividualReference_shouldReturnPractitionerReference() {
		Encounter encounter = new Encounter();
		encounter.addParticipant().getIndividual().setReference("Practitioner/doc-uuid").setDisplay("Dr Test");
		Reference ref = EncounterPractitionerSupport.extractParticipantIndividualReference(encounter);
		assertNotNull(ref);
		assertEquals("Practitioner/doc-uuid", ref.getReference());
	}
	
	@Test
	public void isVitalsEncounter_shouldMatchDisplay() {
		Encounter encounter = new Encounter();
		encounter.addType(new CodeableConcept().addCoding(new Coding().setDisplay("Vitals")));
		assertTrue(EncounterPractitionerSupport.isVitalsEncounter(encounter));
		assertFalse(EncounterPractitionerSupport.isVisitCompleteEncounter(encounter));
	}
	
	@Test
	public void visitBuilder_shouldUseEncounterParticipantForProvenanceAuthor() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-1",
		        (bundle, addedCruids, subject, logPrefix) -> true);
		
		Encounter visitComplete = new Encounter();
		visitComplete.setId("vc-1");
		visitComplete.getSubject().setReference("Patient/p1");
		visitComplete.addType(new CodeableConcept()
		        .addCoding(new Coding().setSystem("http://fhir.openmrs.org/code-system/encounter-type").setDisplay("Visit Complete")));
		visitComplete.addParticipant().getIndividual().setReference("Practitioner/6dea2d57-e84f-482c-9d3b-2e7dcc3501b3");
		builder.addPutResource(visitComplete, "[test]");
		builder.captureProvenanceAuthorsFromEncounter(visitComplete);
		builder.registerProvenanceTarget(ProvenanceAssertionClass.VISIT_COMPLETION, visitComplete, null);
		
		org.hl7.fhir.r4.model.Bundle bundle = builder.build();
		Provenance provenance = null;
		for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.getResource() instanceof Provenance) {
				provenance = (Provenance) entry.getResource();
				if (provenance.getIdElement().getIdPart().contains("visit-completion")) {
					break;
				}
			}
		}
		assertNotNull(provenance);
		assertEquals("Practitioner/6dea2d57-e84f-482c-9d3b-2e7dcc3501b3",
		    provenance.getAgentFirstRep().getWho().getReference());
	}
}
