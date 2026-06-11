package org.openmrs.module.ihshr.backlog;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.fhir.StructuredObsContextSupport;

/**
 * Facade used from lookup loaders and FHIR builders to record §8.6 backlog rows.
 */
public final class UnmappedTermBacklog {
	
	private UnmappedTermBacklog() {
	}
	
	public static void recordMiss(String lookupFile, UnmappedTermLookupType lookupType, String termText) {
		recordMiss(null, lookupFile, lookupType, termText);
	}
	
	public static void recordMiss(UnmappedTermArtifact artifact, String lookupFile, UnmappedTermLookupType lookupType,
	        String termText) {
		if (StringUtils.isBlank(termText) || lookupType == null) {
			return;
		}
		if (!IhshrUnmappedTermServiceImpl.isBacklogEnabled()) {
			return;
		}
		UnmappedTermArtifact resolved = artifact != null && artifact != UnmappedTermArtifact.UNKNOWN ? artifact
		        : UnmappedTermArtifact.fromLookupFile(lookupFile);
		if (resolved == UnmappedTermArtifact.UNKNOWN) {
			return;
		}
		try {
			if (!Context.isSessionOpen()) {
				return;
			}
			IhshrUnmappedTermService service = Context.getRegisteredComponent("ihshrUnmappedTermService",
			    IhshrUnmappedTermService.class);
			if (service != null) {
				service.recordOccurrence(resolved, lookupFile, lookupType, termText);
			}
		}
		catch (Exception ignored) {
			// never break FHIR export
		}
	}
	
	public static void runWithContext(org.hl7.fhir.r4.model.Observation sourceObs, String obsUuid, Integer conceptId,
	        Runnable action) {
		if (action == null) {
			return;
		}
		StructuredObsContextSupport.populateBacklogContext(sourceObs, obsUuid, conceptId);
		try {
			action.run();
		}
		finally {
			UnmappedTermBacklogContext.clear();
		}
	}
	
}
