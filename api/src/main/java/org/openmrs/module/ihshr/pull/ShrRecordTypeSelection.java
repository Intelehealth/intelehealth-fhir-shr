package org.openmrs.module.ihshr.pull;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves {@code recordTypes} for {@link ShrPullView#CUSTOM} requests.
 */
public final class ShrRecordTypeSelection {
	
	private ShrRecordTypeSelection() {
	}
	
	public static List<ShrPullRecordType> resolved(ShrHistoryRequest request) {
		if (request == null || request.getRecordTypes() == null || request.getRecordTypes().isEmpty()) {
			return new ArrayList<ShrPullRecordType>(ShrPullRecordType.defaultCustomTypes());
		}
		return request.getRecordTypes();
	}
	
	public static boolean includes(ShrHistoryRequest request, ShrPullRecordType type) {
		if (request == null || request.getView() != ShrPullView.CUSTOM) {
			return true;
		}
		return resolved(request).contains(type);
	}
	
	public static boolean isEncounterBundled(ShrPullRecordType type) {
		return type == ShrPullRecordType.OBSERVATION || type == ShrPullRecordType.CONDITION
		        || type == ShrPullRecordType.MEDICATION_REQUEST || type == ShrPullRecordType.SERVICE_REQUEST
		        || type == ShrPullRecordType.DOCUMENT_REFERENCE || type == ShrPullRecordType.PROVENANCE;
	}
}
