package org.openmrs.module.ihshr.scheduler;

import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.importing.LocationImportService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenMRS scheduled task that imports {@link org.hl7.fhir.r4.model.Location} resources from the
 * GOFR/SHR FHIR server into local OpenMRS. Register in Admin → Manage Scheduler with class
 * {@code org.openmrs.module.ihshr.scheduler.LocationImportTask}.
 */
public class LocationImportTask extends AbstractTask {
	
	private static final Logger LOG = LoggerFactory.getLogger(LocationImportTask.class);
	
	private static final String LOCATION_IMPORT_SERVICE_BEAN = "ihshrLocationImportService";
	
	@Override
	public void execute() {
		if (isExecuting) {
			LOG.warn("{} is already running; skipping this trigger", getClass().getSimpleName());
			return;
		}
		startExecuting();
		try {
			Context.openSession();
			LocationImportService importService = resolveImportService();
			if (importService == null) {
				LOG.error("LocationImportService is not available; aborting LocationImportTask");
				return;
			}
			LOG.info("LocationImportTask started");
			int created = importService.importLocationsFromGofr();
			LOG.info("LocationImportTask completed; created {} location(s)", created);
		}
		catch (Exception ex) {
			LOG.error("LocationImportTask failed: " + ex.getMessage(), ex);
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
	
	private LocationImportService resolveImportService() {
		try {
			return Context.getRegisteredComponent(LOCATION_IMPORT_SERVICE_BEAN, LocationImportService.class);
		}
		catch (Exception ex) {
			LOG.warn("Falling back to type-based lookup for LocationImportService: {}", ex.getMessage());
			try {
				java.util.List<LocationImportService> components = Context
				        .getRegisteredComponents(LocationImportService.class);
				return (components == null || components.isEmpty()) ? null : components.get(0);
			}
			catch (Exception fallbackEx) {
				LOG.warn("Type-based lookup for LocationImportService failed: {}", fallbackEx.getMessage());
				return null;
			}
		}
	}
	
}
