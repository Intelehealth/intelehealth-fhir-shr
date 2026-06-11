package org.openmrs.module.ihshr.pull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;

public class ShrBundleMergerTest {
	
	@Test
	public void mergeDedupesByResourceTypeAndId() {
		Encounter encounter = new Encounter();
		encounter.setId("enc-1");
		Condition condition = new Condition();
		condition.setId("cond-1");
		Observation obs = new Observation();
		obs.setId("obs-1");
		
		Bundle first = bundleWith(encounter, condition);
		Bundle second = bundleWith(encounter, obs);
		
		Bundle merged = ShrBundleMerger.merge(Arrays.asList(first, second));
		assertEquals(3, merged.getEntry().size());
	}
	
	@Test
	public void extractPaginationLinksCopiesNextLink() {
		Bundle bundle = new Bundle();
		bundle.addLink().setRelation("next").setUrl("http://shr/fhir/Encounter?page=2");
		assertEquals("http://shr/fhir/Encounter?page=2", ShrBundleMerger.extractPaginationLinks(bundle).get("next"));
	}
	
	@Test
	public void resourceKeyUsesFhirTypeAndId() {
		Condition condition = new Condition();
		condition.setId("abc");
		assertEquals("Condition/abc", ShrBundleMerger.resourceKey(condition));
	}
	
	private static Bundle bundleWith(org.hl7.fhir.r4.model.Resource... resources) {
		Bundle bundle = new Bundle();
		for (org.hl7.fhir.r4.model.Resource resource : resources) {
			bundle.addEntry().setResource(resource);
		}
		return bundle;
	}
}
