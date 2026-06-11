package org.openmrs.module.ihshr.scheduler;

import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.backlog.IhshrUnmappedTermService;
import org.openmrs.scheduler.tasks.AbstractTask;

/**
 * Optional OpenMRS scheduler task for doc §8.6 weekly top-50 unmapped term report (server log).
 * Class: {@code org.openmrs.module.ihshr.scheduler.UnmappedTermBacklogReportTask}
 */
public class UnmappedTermBacklogReportTask extends AbstractTask {
	
	@Override
	public void execute() {
		Context.openSession();
		try {
			IhshrUnmappedTermService service = Context.getRegisteredComponent("ihshrUnmappedTermService",
			    IhshrUnmappedTermService.class);
			if (service != null) {
				service.logWeeklyTopUnmappedReport();
			}
		}
		finally {
			Context.closeSession();
		}
	}
	
}
