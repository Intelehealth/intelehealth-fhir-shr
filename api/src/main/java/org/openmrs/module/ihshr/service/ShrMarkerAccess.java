package org.openmrs.module.ihshr.service;

import org.openmrs.api.context.Context;
import org.openmrs.module.ihmodule.api.patientexchange.model.IHMarker;
import org.openmrs.module.ihmodule.api.patientexchange.service.IHMarkerService;

/**
 * Sync cursor access via {@code ihmodule} {@link IHMarkerService} ({@code ih_marker} table). IHSHR
 * does not implement markers locally.
 */
public final class ShrMarkerAccess {
	
	public static final String IHMODULE_MARKER_BEAN = "ihmoduleIHMarkerService";
	
	private ShrMarkerAccess() {
	}
	
	public static String lastSyncTime(String markerName) {
		return markerService().findByName(markerName).getLastSyncTime();
	}
	
	public static IHMarker findMarker(String markerName) {
		return markerService().findByName(markerName);
	}
	
	public static void updateLastSync(String markerName) {
		markerService().updateMarkerByName(markerName);
	}
	
	public static void saveLastSyncTime(String markerName, String lastSyncTime) {
		if (lastSyncTime == null || lastSyncTime.trim().isEmpty()) {
			return;
		}
		IHMarker marker = markerService().findByName(markerName);
		marker.setLastSyncTime(lastSyncTime.trim());
		markerService().save(marker);
	}
	
	private static IHMarkerService markerService() {
		IHMarkerService service = Context.getRegisteredComponent(IHMODULE_MARKER_BEAN, IHMarkerService.class);
		if (service == null) {
			throw new IllegalStateException("ihmodule IHMarkerService not available (bean " + IHMODULE_MARKER_BEAN
			        + "). Install ihmodule.");
		}
		return service;
	}
	
}
