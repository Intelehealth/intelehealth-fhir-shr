package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ClinicalTermNormalizerTest {
	
	@Test
	public void lookupVariants_includesFullTermAndSuffixAfterColon() {
		List<String> variants = ClinicalTermNormalizer.lookupVariants("Eyes: Jaundice-no jaundice seen.");
		assertTrue(variants.contains("Eyes: Jaundice-no jaundice seen"));
		assertTrue(variants.contains("Jaundice-no jaundice seen"));
	}
	
	@Test
	public void lookupVariants_stripsPictureTakenMarker() {
		List<String> variants = ClinicalTermNormalizer.lookupVariants("Diabetes, [picture taken].");
		assertTrue(variants.stream().anyMatch(v -> v.contains("Diabetes")));
	}
}
