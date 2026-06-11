package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;

public class EncounterPartOfSupportTest {
	
	@Test
	public void parseEncounterReferenceId_shouldReadRelativeReference() {
		assertEquals("visit-1", EncounterPartOfSupport.parseEncounterReferenceId(new Reference("Encounter/visit-1")));
	}
	
	@Test
	public void collectPartOfParentIds_shouldGatherDistinctParents() {
		Encounter child = new Encounter();
		child.getPartOf().setReference("Encounter/parent-1");
		Encounter child2 = new Encounter();
		child2.getPartOf().setReference("Encounter/parent-1");
		Set<String> parents = EncounterPartOfSupport.collectPartOfParentIds(Arrays.asList(child, child2));
		assertEquals(Collections.singleton("parent-1"), parents);
	}
	
	@Test
	public void stripUnresolvedPartOf_shouldRemoveUnknownParent() {
		Encounter child = new Encounter();
		child.getPartOf().setReference("Encounter/missing");
		EncounterPartOfSupport.stripUnresolvedPartOf(child, new LinkedHashSet<>(Collections.singleton("other")));
		assertFalse(child.hasPartOf());
	}
	
	@Test
	public void buildVisitParentPlaceholder_shouldCopySubjectFromTemplate() {
		Encounter template = new Encounter();
		template.getSubject().setReference("Patient/p1");
		Encounter parent = EncounterPartOfSupport.buildVisitParentPlaceholder("visit-uuid", template);
		assertEquals("visit-uuid", parent.getIdElement().getIdPart());
		assertEquals("Patient/p1", parent.getSubject().getReference());
		assertNull(EncounterPartOfSupport.parseEncounterReferenceId(null));
	}
	
	@Test
	public void visitBuilder_shouldPrependParentEncounter() {
		VisitTransactionBundleBuilder builder = new VisitTransactionBundleBuilder("visit-1",
		        (bundle, addedCruids, subject, logPrefix) -> true);
		Encounter child = new Encounter();
		child.setId("child-1");
		child.getSubject().setReference("Patient/p1");
		child.getPartOf().setReference("Encounter/visit-1");
		builder.addPutResource(child, "[test]");
		
		Encounter parent = EncounterPartOfSupport.buildVisitParentPlaceholder("visit-1", child);
		assertTrue(builder.addPutResource(parent, "[test]", true));
		
		assertEquals("visit-1", builder.getIncludedEncounterIds().iterator().next());
	}
}
