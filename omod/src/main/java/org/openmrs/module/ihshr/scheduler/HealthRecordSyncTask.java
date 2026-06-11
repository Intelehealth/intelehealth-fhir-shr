package org.openmrs.module.ihshr.scheduler;

import org.openmrs.api.context.Context;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenMRS scheduled task that delegates to {@link DataSendToSHR#syncHealthRecords()}. Register in
 * Admin → Manage Scheduler with class
 * {@code org.openmrs.module.ihshr.scheduler.HealthRecordSyncTask} .
 */
public class HealthRecordSyncTask extends AbstractTask {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HealthRecordSyncTask.class);
	
	private static final String DATA_SEND_TO_SHR_BEAN = "healthRecordDataSendToSHR";
	
	@Override
	public void execute() {
		if (isExecuting) {
			LOGGER.warn("{} is already running; skipping this trigger", getClass().getSimpleName());
			return;
		}
		
		startExecuting();
		try {
			Context.openSession();
			DataSendToSHR dataSendToSHR = resolveDataSendToSHR();
			if (dataSendToSHR == null) {
				LOGGER.error("DataSendToSHR component is not available; aborting HealthRecordSyncTask");
				return;
			}
			LOGGER.info("HealthRecordSyncTask started");
			dataSendToSHR.syncHealthRecords();
			LOGGER.info("HealthRecordSyncTask completed");
		}
		catch (Exception ex) {
			LOGGER.error("HealthRecordSyncTask failed: " + ex.getMessage(), ex);
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
	
	private DataSendToSHR resolveDataSendToSHR() {
		try {
			return Context.getRegisteredComponent(DATA_SEND_TO_SHR_BEAN, DataSendToSHR.class);
		}
		catch (Exception ex) {
			LOGGER.warn("Falling back to type-based component lookup for DataSendToSHR: {}", ex.getMessage());
			java.util.List<DataSendToSHR> components = Context.getRegisteredComponents(DataSendToSHR.class);
			return (components == null || components.isEmpty()) ? null : components.get(0);
		}
	}
}
