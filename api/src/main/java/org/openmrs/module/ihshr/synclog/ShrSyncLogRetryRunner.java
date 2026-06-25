package org.openmrs.module.ihshr.synclog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one SHR sync-log retry in an isolated transaction so a constraint violation or Hibernate
 * error on one visit does not poison the scheduler session for other visits.
 */
@Component
public class ShrSyncLogRetryRunner {
	
	@Autowired
	private ShrSyncLogService syncLogService;
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean replayOne(IntelehealthShrSyncLog failed) {
		return syncLogService.replaySingleFailedRow(failed);
	}
	
}
