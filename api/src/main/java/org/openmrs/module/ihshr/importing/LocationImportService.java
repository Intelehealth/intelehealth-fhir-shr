package org.openmrs.module.ihshr.importing;

import java.text.ParseException;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.openmrs.User;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihmodule.api.patientexchange.model.IHMarker;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.service.CommonOperationService;
import org.openmrs.module.ihshr.service.ShrMarkerAccess;
import org.openmrs.module.ihshr.utils.DateUtils;
import org.openmrs.module.ihshr.utils.IHConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.param.DateRangeParam;

/**
 * Creates a Location on the local OpenMRS FHIR endpoint (seam for unit tests).
 */
@FunctionalInterface
interface FhirLocationCreator {
	
	MethodOutcome create(Location remote);
	
}

/**
 * Imports {@link Location} resources from the GOFR/SHR FHIR server into local OpenMRS (mirrors
 * {@code DataImport#importResource(Location.class, importLocation)} from ih-fhir-shr).
 */
@Service("ihshrLocationImportService")
public class LocationImportService extends IHConstant {
	
	public static final String FACILITY_LOCATION_TAG = "Facility";
	
	private static final Logger LOG = LoggerFactory.getLogger(LocationImportService.class);
	
	@Autowired
	private FhirConfig fhirConfig;
	
	@Autowired
	private CommonOperationService commonOperationService;
	
	public int importLocationsFromGofr() throws ParseException {
		String markerName = getImportLocation();
		IHMarker marker = ShrMarkerAccess.findMarker(markerName);
		LOG.info("Location import starting; marker={} lastSyncTime={}", markerName, marker.getLastSyncTime());
		
		String lastUpdatedSince = DateUtils.toFhirLastUpdatedParam(marker.getLastSyncTime());
		IQuery<Bundle> searchQuery = fhirConfig.getGOFRFhirContext().search().forResource(Location.class)
		        .lastUpdated(new DateRangeParam(lastUpdatedSince, null)).sort().ascending("_lastUpdated")
		        .returnBundle(Bundle.class);
		Bundle bundle = searchQuery.execute();
		if (bundle == null || !bundle.hasEntry()) {
			LOG.info("Location import: no updated Location resources from GOFR");
			return 0;
		}
		
		int created = 0;
		int tagged = 0;
		String latestSyncTime = marker.getLastSyncTime();
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (!(entry.getResource() instanceof Location)) {
				continue;
			}
			Location remote = (Location) entry.getResource();
			if (StringUtils.isBlank(remote.getName())) {
				continue;
			}
			String remoteUuid = remote.getIdElement().getIdPart();
			if (importSingleLocation(remote, remoteUuid, commonOperationService, this::createLocalLocation)) {
				created++;
			}
			String openMrsUuid = resolveOpenMrsLocationUuid(remoteUuid, remote.getName(), commonOperationService);
			if (StringUtils.isNotBlank(openMrsUuid)
			        && ensureFacilityTag(openMrsUuid, Context.getLocationService(), resolveCreatorUser())) {
				tagged++;
			}
			if (remote.hasMeta() && remote.getMeta().hasLastUpdated()) {
				latestSyncTime = remote.getMeta().getLastUpdated().toInstant().toString();
			}
		}
		if (StringUtils.isNotBlank(latestSyncTime)) {
			ShrMarkerAccess.saveLastSyncTime(markerName, latestSyncTime);
		}
		LOG.info("Location import finished; created={}, facilityTagApplied={}, entries={}", created, tagged, bundle
		        .getEntry().size());
		return created;
	}
	
	private MethodOutcome createLocalLocation(Location remote) {
		return fhirConfig.getLocalOpenMRSFhirContext().create().resource(remote).execute();
	}
	
	boolean importSingleLocation(Location remote, String remoteUuid, CommonOperationService ops, FhirLocationCreator creator) {
		Integer existingId = ops.findLocationIdByUuidOrName(remoteUuid, remote.getName());
		if (existingId != null && existingId > 0) {
			LOG.debug("Location already exists uuid={} name={} location_id={}", remoteUuid, remote.getName(), existingId);
			return false;
		}
		try {
			MethodOutcome outcome = creator.create(remote);
			if (outcome == null || outcome.getId() == null || StringUtils.isBlank(outcome.getId().getIdPart())) {
				LOG.warn("Location create returned no id for remote uuid={} name={}", remoteUuid, remote.getName());
				return false;
			}
			Integer localId = ops.findLocationIdByUuidOrName(outcome.getId().getIdPart(), remote.getName());
			if (localId == null || localId <= 0) {
				LOG.warn("Location create succeeded but local row not found for name={}", remote.getName());
				return false;
			}
			ops.updateLocationUuid(localId, remoteUuid);
			LOG.info("Imported Location uuid={} name={} (location_id={})", remoteUuid, remote.getName(), localId);
			return true;
		}
		catch (Exception ex) {
			LOG.error("Failed to import Location uuid=" + remoteUuid + " name=" + remote.getName() + ": " + ex.getMessage(),
			    ex);
			return false;
		}
	}
	
	private String resolveOpenMrsLocationUuid(String remoteUuid, String name, CommonOperationService ops) {
		Integer locationId = ops.findLocationIdByUuidOrName(remoteUuid, name);
		if (locationId == null || locationId <= 0) {
			return null;
		}
		return ops.findLocationUuidById(locationId);
	}
	
	/**
	 * Ensures OpenMRS {@link org.openmrs.LocationTag} {@value #FACILITY_LOCATION_TAG} exists and is
	 * assigned to the location.
	 */
	public boolean ensureFacilityTag(String locationUuid) {
		return ensureFacilityTag(locationUuid, Context.getLocationService(), resolveCreatorUser());
	}
	
	boolean ensureFacilityTag(String locationUuid, LocationService locationService, User creator) {
		if (StringUtils.isBlank(locationUuid)) {
			return false;
		}
		org.openmrs.Location location = locationService.getLocationByUuid(locationUuid.trim());
		if (location == null) {
			return false;
		}
		org.openmrs.LocationTag facilityTag = locationService.getLocationTagByName(FACILITY_LOCATION_TAG);
		if (facilityTag == null) {
			facilityTag = new org.openmrs.LocationTag();
			facilityTag.setName(FACILITY_LOCATION_TAG);
			facilityTag.setDescription("Imported facility locations from GOFR/SHR");
			facilityTag.setCreator(creator);
			facilityTag.setDateCreated(new Date());
			facilityTag = locationService.saveLocationTag(facilityTag);
			LOG.info("Created OpenMRS location tag '{}'", FACILITY_LOCATION_TAG);
		}
		if (location.getTags().contains(facilityTag)) {
			return true;
		}
		location.addTag(facilityTag);
		locationService.saveLocation(location);
		LOG.debug("Applied location tag '{}' to uuid={}", FACILITY_LOCATION_TAG, locationUuid);
		return true;
	}
	
	private User resolveCreatorUser() {
		User creator = Context.getAuthenticatedUser();
		if (creator == null) {
			creator = Context.getUserService().getUser(1);
		}
		return creator;
	}
	
}
