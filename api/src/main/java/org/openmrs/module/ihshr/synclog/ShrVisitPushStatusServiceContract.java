package org.openmrs.module.ihshr.synclog;

public interface ShrVisitPushStatusServiceContract {
	
	void recordSuccess(String visitUuid);
	
	void recordPermanentFailure(String visitUuid, String failureReason);
}
