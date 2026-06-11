package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.openmrs.module.ihshr.fhir.provenance.ProvenanceAssertionClass;

public class VisitTransactionBundleBuilderTest {
	
	@Test
	public void build_shouldDeduplicateResourcesAndAppendProvenance() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-uuid-1",
		        (bundle, addedCruids, subject, logPrefix) -> {
			        Reference rewritten = new Reference("urn:uuid:ih-shr-patient-test-cruid");
			        ShrCruidPatientSupport.rewriteSubjectReference(subject, rewritten.getReference());
			        return true;
		        });
		
		Observation obs = new Observation();
		obs.addIdentifier(new Identifier().setValue("obs-1"));
		obs.getSubject().setReference("Patient/local-patient");
		builder.addPutResource(obs, "[test]");
		builder.registerProvenanceTarget(ProvenanceAssertionClass.VITALS, obs, "obs-1");
		
		Observation obsDuplicate = new Observation();
		obsDuplicate.addIdentifier(new Identifier().setValue("obs-1"));
		obsDuplicate.getSubject().setReference("Patient/local-patient");
		builder.addPutResource(obsDuplicate, "[test]");
		
		Encounter encounter = new Encounter();
		encounter.setId("enc-1");
		encounter.getSubject().setReference("Patient/local-patient");
		builder.addPutResource(encounter, "[test]");
		builder.registerProvenanceTarget(ProvenanceAssertionClass.VISIT_COMPLETION, encounter, null);
		
		Bundle bundle = builder.build();
		int clinical = 0;
		int provenance = 0;
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.getResource() instanceof Provenance) {
				provenance++;
			} else if (!isNonClinicalBundleResource(entry.getResource())) {
				clinical++;
			}
		}
		assertTrue(bundle.getEntry().stream().anyMatch(e -> e.getResource() instanceof Device));
		assertTrue(bundle.getEntry().stream().anyMatch(e -> e.getResource() instanceof Organization));
		assertEquals(2, clinical);
		assertTrue(provenance >= 2);
		assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
	}
	
	@Test
	public void build_shouldStripDomainResourceNarrativeButPreserveNotes() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-narrative",
		        (bundle, addedCruids, subject, logPrefix) -> true);
		
		Observation obs = new Observation();
		obs.setId("obs-with-narrative");
		obs.getSubject().setReference("Patient/p1");
		Narrative narrative = new Narrative();
		narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
		narrative.setDivAsString("<div>generated</div>");
		obs.setText(narrative);
		obs.addNote(new Annotation().setText("clinical note text"));
		builder.addPutResource(obs, "[test]");
		
		Bundle bundle = builder.build();
		Observation built = (Observation) bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
		        .filter(Observation.class::isInstance).findFirst().orElseThrow(() -> new AssertionError("missing obs"));
		assertFalse(built.hasText());
		assertEquals("clinical note text", built.getNoteFirstRep().getText());
	}
	
	@Test
	public void addPutResource_shouldReplaceSameConditionId() {
		VisitPushCruidSupport cruid = (bundle, addedCruids, subject, logPrefix) -> true;
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-2", cruid);
		
		Condition first = new Condition();
		first.addIdentifier(new Identifier().setValue("cond-1"));
		first.getSubject().setReference("Patient/p1");
		builder.addPutResource(first, "[test]");
		
		Condition second = new Condition();
		second.addIdentifier(new Identifier().setValue("cond-1"));
		second.getSubject().setReference("Patient/p1");
		second.setClinicalStatus(new org.hl7.fhir.r4.model.CodeableConcept().addCoding(new org.hl7.fhir.r4.model.Coding()
		        .setCode("resolved")));
		builder.addPutResource(second, "[test]");
		
		Bundle bundle = builder.build();
		int conditions = 0;
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.getResource() instanceof Condition) {
				conditions++;
				Condition c = (Condition) entry.getResource();
				assertTrue(c.getClinicalStatus().getCodingFirstRep().hasCode());
			}
		}
		assertEquals(1, conditions);
	}
	
	private static boolean isNonClinicalBundleResource(org.hl7.fhir.r4.model.Resource resource) {
		return resource instanceof org.hl7.fhir.r4.model.Patient || resource instanceof Provenance
		        || resource instanceof Device || resource instanceof Organization || resource instanceof Practitioner;
	}
}
