package org.openmrs.module.ihshr.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.junit.Test;
import org.openmrs.module.ihshr.config.PublishedConfigShrSyncGateService;
import org.openmrs.module.ihshr.domain.FhirResponse;
import org.openmrs.module.ihshr.synclog.IntelehealthShrSyncLog;
import org.openmrs.module.ihshr.synclog.ObsPushContext;
import org.openmrs.module.ihshr.synclog.ShrSyncLogService;

public class DataSendToSHRFhirSyncGateTest {
	
	@Test
	public void enqueueVisitBundlePush_createsSyncLogWithoutPostingWhenFhirDisabled() throws Exception {
		DataSendToSHR sender = new DataSendToSHR();
		TrackingShrSyncLogService syncLogService = new TrackingShrSyncLogService();
		setField(sender, "shrSyncLogService", syncLogService);
		setField(sender, "publishedConfigShrSyncGateService", new FixedShrSyncGate(false));
		
		Bundle bundle = new Bundle();
		bundle.setType(BundleType.TRANSACTION);
		ObsPushContext context = new ObsPushContext("visit-uuid-deferred", "encounter-uuid", null);
		
		invokeEnqueueVisitBundlePush(sender, bundle, context);
		
		assertEquals(1, syncLogService.createPendingCalls);
		assertEquals(0, syncLogService.tryPushPendingRowCalls);
		assertNotNull(syncLogService.lastPendingContext);
		assertEquals("visit-uuid-deferred", syncLogService.lastPendingContext.getVisitUuid());
	}
	
	private static void invokeEnqueueVisitBundlePush(DataSendToSHR sender, Bundle bundle, ObsPushContext context)
	        throws Exception {
		Method method = DataSendToSHR.class.getDeclaredMethod("enqueueVisitBundlePush", Bundle.class, ObsPushContext.class,
		    String.class);
		method.setAccessible(true);
		method.invoke(sender, bundle, context, "[VisitPush]");
	}
	
	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
	
	private static final class FixedShrSyncGate extends PublishedConfigShrSyncGateService {
		
		private final boolean enabled;
		
		FixedShrSyncGate(boolean enabled) {
			this.enabled = enabled;
		}
		
		@Override
		public boolean isShrSyncEnabled() {
			return enabled;
		}
	}
	
	static final class TrackingShrSyncLogService implements ShrSyncLogService {
		
		private int createPendingCalls;
		
		private int tryPushPendingRowCalls;
		
		private ObsPushContext lastPendingContext;
		
		@Override
		public IntelehealthShrSyncLog createPending(ObsPushContext context, String requestBundleJson) {
			createPendingCalls++;
			lastPendingContext = context;
			IntelehealthShrSyncLog row = new IntelehealthShrSyncLog();
			row.setId(99L);
			return row;
		}
		
		@Override
		public void markSuccess(IntelehealthShrSyncLog row, FhirResponse response, Bundle requestBundle,
		        Bundle responseBundle) {
			/* not used */
		}
		
		@Override
		public void markFailed(IntelehealthShrSyncLog row, FhirResponse response, String failureReason, boolean permanent) {
			/* not used */
		}
		
		@Override
		public void completePendingPush(IntelehealthShrSyncLog pending, FhirResponse response, Bundle requestBundle) {
			/* not used */
		}
		
		@Override
		public void tryPushPendingRow(IntelehealthShrSyncLog pending, Bundle requestBundle) {
			tryPushPendingRowCalls++;
		}
		
		@Override
		public void refreshRequestBundle(IntelehealthShrSyncLog row, Bundle requestBundle) {
			/* not used */
		}
		
		@Override
		public int runSyncCycle(int limitPerCycle) {
			return 0;
		}
		
		@Override
		public int runRetryCycle(int limitPerCycle) {
			return 0;
		}
		
		@Override
		public int runPendingPushCycle(int limitPerCycle) {
			return 0;
		}
	}
}
