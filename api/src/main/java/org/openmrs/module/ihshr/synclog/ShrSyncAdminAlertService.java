package org.openmrs.module.ihshr.synclog;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("shrSyncAdminAlertService")
public class ShrSyncAdminAlertService implements ShrSyncAdminAlertContract {
	
	private static final Logger LOG = LoggerFactory.getLogger(ShrSyncAdminAlertService.class);
	
	@Autowired
	private ShrVisitPushStatusServiceContract visitPushStatusService;
	
	@Override
	public void alertPermanentPushFailure(IntelehealthShrSyncLog row) {
		if (row == null) {
			return;
		}
		LOG.error(
		    "ADMIN ALERT: SHR push permanently failed — sync_log_id={} visit_uuid={} attempt={} http_status={} reason={}",
		    row.getId(), row.getVisitUuid(), row.getAttemptNumber(), row.getHttpStatusCode(), row.getFailureReason());
		if (StringUtils.isNotBlank(row.getVisitUuid())) {
			visitPushStatusService.recordPermanentFailure(row.getVisitUuid(), row.getFailureReason());
		}
	}
}
