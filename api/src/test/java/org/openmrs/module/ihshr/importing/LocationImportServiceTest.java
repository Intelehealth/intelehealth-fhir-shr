package org.openmrs.module.ihshr.importing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Location;
import org.junit.Test;
import org.openmrs.User;
import org.openmrs.api.LocationService;
import org.openmrs.module.ihshr.service.CommonOperationService;

import ca.uhn.fhir.rest.api.MethodOutcome;

public class LocationImportServiceTest {
	
	@Test
	public void importSingleLocation_skipsWhenLocationAlreadyExists() {
		LocationImportService service = new LocationImportService();
		TrackingLocationOps ops = new TrackingLocationOps();
		ops.existingByRemote = Integer.valueOf(7);
		Location remote = location("gofr-remote-uuid", "Dhaka Clinic");
		
		boolean created = service.importSingleLocation(remote, "gofr-remote-uuid", ops, unused -> {
			throw new AssertionError("create should not be called when location exists");
		});
		
		assertFalse(created);
		assertEquals(0, ops.updateCalls);
	}
	
	@Test
	public void importSingleLocation_createsLocationAndUpdatesUuid() {
		LocationImportService service = new LocationImportService();
		TrackingLocationOps ops = new TrackingLocationOps();
		ops.existingByRemote = Integer.valueOf(0);
		ops.existingByLocalUuid = Integer.valueOf(42);
		Location remote = location("gofr-remote-uuid", "Dhaka Clinic");
		
		boolean created = service.importSingleLocation(remote, "gofr-remote-uuid", ops, loc -> {
			assertEquals("Dhaka Clinic", loc.getName());
			MethodOutcome outcome = new MethodOutcome();
			outcome.setId(new IdType("Location/temp-local-uuid"));
			return outcome;
		});
		
		assertTrue(created);
		assertEquals(1, ops.updateCalls);
		assertEquals(Integer.valueOf(42), ops.lastUpdateId);
		assertEquals("gofr-remote-uuid", ops.lastUpdateUuid);
	}
	
	@Test
	public void importSingleLocation_returnsFalseWhenCreateReturnsNoId() {
		LocationImportService service = new LocationImportService();
		TrackingLocationOps ops = new TrackingLocationOps();
		Location remote = location("gofr-remote-uuid", "Dhaka Clinic");
		
		boolean created = service.importSingleLocation(remote, "gofr-remote-uuid", ops, loc -> new MethodOutcome());
		
		assertFalse(created);
		assertEquals(0, ops.updateCalls);
	}
	
	@Test
	public void ensureFacilityTag_createsTagAndAppliesToLocation() {
		LocationImportService service = new LocationImportService();
		org.openmrs.Location openMrsLocation = new org.openmrs.Location();
		openMrsLocation.setUuid("openmrs-loc-uuid");
		openMrsLocation.setTags(new java.util.HashSet<org.openmrs.LocationTag>());
		RecordingLocationService locationService = new RecordingLocationService(openMrsLocation);
		User creator = new User();
		creator.setUserId(1);
		
		boolean applied = service.ensureFacilityTag("openmrs-loc-uuid", locationService.service, creator);
		
		assertTrue(applied);
		assertNotNull(locationService.savedTag);
		assertEquals(LocationImportService.FACILITY_LOCATION_TAG, locationService.savedTag.getName());
		assertEquals(creator, locationService.savedTag.getCreator());
		assertEquals(1, locationService.saveLocationCalls);
		assertTrue(openMrsLocation.getTags().contains(locationService.savedTag));
	}
	
	@Test
	public void ensureFacilityTag_isIdempotentWhenTagAlreadyPresent() {
		LocationImportService service = new LocationImportService();
		org.openmrs.LocationTag existingTag = new org.openmrs.LocationTag();
		existingTag.setName(LocationImportService.FACILITY_LOCATION_TAG);
		org.openmrs.Location openMrsLocation = new org.openmrs.Location();
		openMrsLocation.setUuid("openmrs-loc-uuid");
		openMrsLocation.setTags(new java.util.HashSet<org.openmrs.LocationTag>());
		openMrsLocation.addTag(existingTag);
		RecordingLocationService locationService = new RecordingLocationService(openMrsLocation);
		locationService.tagByName = existingTag;
		
		boolean applied = service.ensureFacilityTag("openmrs-loc-uuid", locationService.service, new User());
		
		assertTrue(applied);
		assertEquals(0, locationService.saveLocationCalls);
		assertEquals(0, locationService.saveTagCalls);
	}
	
	@Test
	public void ensureFacilityTag_returnsFalseForBlankUuidOrMissingLocation() {
		LocationImportService service = new LocationImportService();
		RecordingLocationService locationService = new RecordingLocationService(null);
		
		assertFalse(service.ensureFacilityTag("", locationService.service, new User()));
		assertFalse(service.ensureFacilityTag("missing-uuid", locationService.service, new User()));
	}
	
	private static Location location(String uuid, String name) {
		Location remote = new Location();
		remote.setId(uuid);
		remote.setName(name);
		return remote;
	}
	
	private static final class TrackingLocationOps extends CommonOperationService {
		
		private Integer existingByRemote = Integer.valueOf(0);
		
		private Integer existingByLocalUuid = Integer.valueOf(0);
		
		private int updateCalls;
		
		private Integer lastUpdateId;
		
		private String lastUpdateUuid;
		
		@Override
		public Integer findLocationIdByUuidOrName(String uuid, String name) {
			if ("gofr-remote-uuid".equals(uuid)) {
				return existingByRemote;
			}
			if ("temp-local-uuid".equals(uuid)) {
				return existingByLocalUuid;
			}
			return Integer.valueOf(0);
		}
		
		@Override
		public void updateLocationUuid(Integer locationId, String uuid) {
			updateCalls++;
			lastUpdateId = locationId;
			lastUpdateUuid = uuid;
		}
	}
	
	private static final class RecordingLocationService {
		
		private final org.openmrs.Location location;
		
		private final LocationService service;
		
		private org.openmrs.LocationTag tagByName;
		
		private org.openmrs.LocationTag savedTag;
		
		private int saveLocationCalls;
		
		private int saveTagCalls;
		
		RecordingLocationService(org.openmrs.Location location) {
			this.location = location;
			this.service = (LocationService) java.lang.reflect.Proxy.newProxyInstance(LocationService.class.getClassLoader(),
			    new Class<?>[] { LocationService.class }, (proxy, method, args) -> {
				    switch (method.getName()) {
				        case "getLocationByUuid":
					        return RecordingLocationService.this.location != null && args[0] != null
					                && args[0].equals(RecordingLocationService.this.location.getUuid())
					                        ? RecordingLocationService.this.location
					                        : null;
				        case "getLocationTagByName":
					        return tagByName;
				        case "saveLocationTag":
					        saveTagCalls++;
					        savedTag = (org.openmrs.LocationTag) args[0];
					        tagByName = savedTag;
					        return savedTag;
				        case "saveLocation":
					        saveLocationCalls++;
					        return args[0];
				        default:
					        Class<?> returnType = method.getReturnType();
					        if (returnType.equals(boolean.class)) {
						        return Boolean.FALSE;
					        }
					        if (returnType.equals(int.class)) {
						        return 0;
					        }
					        if (returnType.equals(long.class)) {
						        return 0L;
					        }
					        if (returnType.isPrimitive()) {
						        return 0;
					        }
					        return null;
				    }
			    });
		}
	}
}
