package org.openmrs.module.ihshr.synclog;

/**
 * Push attempt status for {@code intelehealth_shr_sync_log} (doc §3.1 step 9/16, §13.1).
 */
public enum ShrSyncLogStatus {
	
	PENDING, SUCCESS, FAILED, FAILED_PERMANENT
}
