package org.openmrs.module.ihshr.synclog;

/**
 * Regenerates a visit transaction bundle from OpenMRS and pushes it to SHR for an existing sync-log
 * row (deferred push or retry).
 */
public interface ShrVisitSyncPushContract {
	
	/**
	 * @return {@code true} if the push attempt was processed (success or failure recorded on
	 *         {@code pushRow})
	 */
	boolean pushVisitForSyncLog(IntelehealthShrSyncLog pushRow, String operationLabel);
}
