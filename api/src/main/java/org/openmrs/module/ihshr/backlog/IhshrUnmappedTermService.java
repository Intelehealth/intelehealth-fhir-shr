package org.openmrs.module.ihshr.backlog;

import java.util.List;

/**
 * §8.6 unmapped SNOMED / lookup term backlog.
 */
public interface IhshrUnmappedTermService {
	
	void recordOccurrence(UnmappedTermArtifact artifact, String lookupFile, UnmappedTermLookupType lookupType,
	        String termText);
	
	List<UnmappedTermAggregate> getTopUnmapped(UnmappedTermArtifact artifact, int limit, boolean unresolvedOnly);
	
	int markResolved(UnmappedTermArtifact artifact, String termText);
	
	String exportTopUnmappedCsv(UnmappedTermArtifact artifact, int limit);
	
	void logWeeklyTopUnmappedReport();
	
}
