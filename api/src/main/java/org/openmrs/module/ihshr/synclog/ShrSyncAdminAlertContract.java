package org.openmrs.module.ihshr.synclog;

/**
 * Admin notification when a visit push reaches {@link ShrSyncLogStatus#FAILED_PERMANENT} (doc
 * §13.3).
 */
public interface ShrSyncAdminAlertContract {
	
	void alertPermanentPushFailure(IntelehealthShrSyncLog row);
}
