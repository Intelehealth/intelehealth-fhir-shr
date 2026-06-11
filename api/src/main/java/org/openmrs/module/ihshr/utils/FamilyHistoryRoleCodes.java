package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklog;
import org.openmrs.module.ihshr.backlog.UnmappedTermLookupType;
import org.openmrs.module.ihshr.config.ShrLookupLoader;

/**
 * Relative role codes from {@code family-relationships.json} (doc §8.2 / §16.3).
 */
public final class FamilyHistoryRoleCodes {
	
	public static final String LOOKUP_FILE = "family-relationships.json";
	
	private FamilyHistoryRoleCodes() {
	}
	
	public static String lookupRoleCode(String relativeLabel) {
		if (StringUtils.isBlank(relativeLabel)) {
			return null;
		}
		Coding coding = ShrLookupLoader.lookupMapping(LOOKUP_FILE, relativeLabel);
		if (coding != null) {
			return coding.getCode();
		}
		UnmappedTermBacklog.recordMiss(UnmappedTermArtifact.FAMILY_HISTORY_RELATIONSHIP, LOOKUP_FILE,
		    UnmappedTermLookupType.MAPPING, relativeLabel);
		return null;
	}
	
}
