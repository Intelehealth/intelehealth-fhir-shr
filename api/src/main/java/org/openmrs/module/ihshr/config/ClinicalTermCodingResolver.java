package org.openmrs.module.ihshr.config;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklog;
import org.openmrs.module.ihshr.backlog.UnmappedTermLookupType;

/**
 * Three-layer clinical term coding: (1) OpenMRS concept dictionary, (2) side-loaded JSON lookup,
 * (3) callers always retain {@code code.text} when this returns null.
 */
public final class ClinicalTermCodingResolver {
	
	private ClinicalTermCodingResolver() {
	}
	
	public static Coding resolveMapping(String term, String jsonLookupFile, UnmappedTermArtifact artifact) {
		return resolveMapping(term, jsonLookupFile, artifact, term);
	}
	
	public static Coding resolveMapping(String term, String jsonLookupFile, UnmappedTermArtifact artifact, String backlogTerm) {
		Coding coding = lookupMapping(term, jsonLookupFile);
		if (coding == null) {
			recordMissIfConfigured(jsonLookupFile, artifact, UnmappedTermLookupType.MAPPING, backlogTerm);
		}
		return coding;
	}
	
	/**
	 * Layer 1 then Layer 2 without recording an unmapped-term backlog row (for alternate-key
	 * attempts).
	 */
	public static Coding lookupMapping(String term, String jsonLookupFile) {
		if (StringUtils.isBlank(term)) {
			return null;
		}
		Coding coding = OpenMrsConceptCodingResolver.lookupByTerm(term);
		if (coding == null && StringUtils.isNotBlank(jsonLookupFile)) {
			coding = ShrLookupLoader.lookupMapping(jsonLookupFile, term);
		}
		return coding;
	}
	
	public static Coding resolveCategory(String categoryName, String jsonLookupFile, UnmappedTermArtifact artifact) {
		Coding coding = lookupCategory(categoryName, jsonLookupFile);
		if (coding == null) {
			recordMissIfConfigured(jsonLookupFile, artifact, UnmappedTermLookupType.CATEGORY, categoryName);
		}
		return coding;
	}
	
	public static Coding lookupCategory(String categoryName, String jsonLookupFile) {
		if (StringUtils.isBlank(categoryName)) {
			return null;
		}
		Coding coding = OpenMrsConceptCodingResolver.lookupByTerm(categoryName);
		if (coding == null && StringUtils.isNotBlank(jsonLookupFile)) {
			coding = ShrLookupLoader.lookupCategory(jsonLookupFile, categoryName);
		}
		return coding;
	}
	
	private static void recordMissIfConfigured(String jsonLookupFile, UnmappedTermArtifact artifact,
	        UnmappedTermLookupType lookupType, String termText) {
		if (artifact == null || artifact == UnmappedTermArtifact.UNKNOWN || StringUtils.isBlank(termText)) {
			return;
		}
		if (StringUtils.isBlank(jsonLookupFile) && StringUtils.isBlank(artifact.getDefaultLookupFile())) {
			return;
		}
		String lookupFile = StringUtils.isNotBlank(jsonLookupFile) ? jsonLookupFile : artifact.getDefaultLookupFile();
		UnmappedTermBacklog.recordMiss(artifact, lookupFile, lookupType, termText);
	}
	
}
