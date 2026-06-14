package org.openmrs.module.ihshr.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;
import org.openmrs.module.ihshr.importing.LocationImportService;

public class LocationImportTaskTest {
	
	@Test
	public void resolveImportService_returnsNullWhenSpringContextUnavailable() throws Exception {
		LocationImportTask task = new LocationImportTask();
		
		LocationImportService resolved = invokeResolveImportService(task);
		
		assertNull(resolved);
	}
	
	@Test
	public void locationImportServiceBeanName_matchesSpringRegistration() throws Exception {
		Field field = LocationImportTask.class.getDeclaredField("LOCATION_IMPORT_SERVICE_BEAN");
		field.setAccessible(true);
		
		assertEquals("ihshrLocationImportService", field.get(null));
	}
	
	private static LocationImportService invokeResolveImportService(LocationImportTask task) throws Exception {
		Method method = LocationImportTask.class.getDeclaredMethod("resolveImportService");
		method.setAccessible(true);
		return (LocationImportService) method.invoke(task);
	}
}
