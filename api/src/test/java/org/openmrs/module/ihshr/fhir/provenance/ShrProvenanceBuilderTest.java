package org.openmrs.module.ihshr.fhir.provenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.Test;

public class ShrProvenanceBuilderTest {
	
	@Test
	public void build_shouldSetIdentifierTargetsAndAgents() {
		Provenance provenance = ShrProvenanceBuilder.build(ProvenanceAssertionClass.DIAGNOSES, "visit-uuid-1",
		    Arrays.asList("Condition/cond-1", "Condition/cond-2"), new Date(1_700_000_000_000L),
		    new Date(1_700_000_100_000L), "diagnosis-obs-uuid", ShrProvenanceReferences.doctorAuthorReference());
		
		assertEquals("visit-uuid-1--prov-diagnosis", provenance.getIdElement().getIdPart());
		Identifier extIdentifier = (Identifier) provenance.getExtensionByUrl(
		    ProvenanceConstants.PROVENANCE_IDENTIFIER_EXTENSION_URL).getValue();
		assertEquals(ProvenanceConstants.VISIT_IDENTIFIER_SYSTEM, extIdentifier.getSystem());
		assertEquals("visit-uuid-1::prov-diagnosis", extIdentifier.getValue());
		assertEquals(2, provenance.getTarget().size());
		assertEquals("Condition/cond-1", provenance.getTarget().get(0).getReference());
		assertEquals(2, provenance.getAgent().size());
		assertTrue(provenance.getAgent().stream().anyMatch(a -> "author".equals(a.getType().getCodingFirstRep().getCode())));
		assertTrue(provenance.getAgent().stream()
		        .anyMatch(a -> "transmitter".equals(a.getType().getCodingFirstRep().getCode())));
		assertEquals(1, provenance.getEntity().size());
		assertEquals(ProvenanceConstants.OPENMRS_OBS_IDENTIFIER_SYSTEM,
		    provenance.getEntityFirstRep().getWhat().getIdentifier().getSystem());
	}
}
