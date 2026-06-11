package org.openmrs.module.ihshr.scheduler;

import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.synclog.ShrSyncLogService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays deferred and failed SHR visit pushes from {@code intelehealth_shr_sync_log} (doc §3.2,
 * §13.4). Register to run every 60 seconds.
 */
public class ShrSyncRetryTask extends AbstractTask {
	
	private static final Logger LOG = LoggerFactory.getLogger(ShrSyncRetryTask.class);
	
	private static final int LIMIT_PER_CYCLE = 20;
	
	@Override
	public void execute() {
		if (isExecuting) {
			LOG.warn("{} is already running; skipping", getClass().getSimpleName());
			return;
		}
		startExecuting();
		try {
			Context.openSession();
			ShrSyncLogService service = Context.getRegisteredComponent("shrSyncLogService", ShrSyncLogService.class);
			if (service == null) {
				LOG.error("shrSyncLogService not available; aborting ShrSyncRetryTask");
				return;
			}
			int processed = service.runSyncCycle(LIMIT_PER_CYCLE);
			if (processed > 0) {
				LOG.info("ShrSyncRetryTask processed {} deferred or failed visit push(es)", processed);
			}
		}
		catch (Exception ex) {
			LOG.error("ShrSyncRetryTask failed: " + ex.getMessage(), ex);
		}
		finally {
			try {
				Context.closeSession();
			}
			finally {
				stopExecuting();
			}
		}
	}
}
