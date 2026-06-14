package org.openmrs.module.ihshr.synclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.Test;
import org.openmrs.module.ihshr.config.PublishedConfigShrSyncGateService;
import org.openmrs.module.ihshr.domain.FhirResponse;

public class ShrSyncLogServiceImplTest {
	
	@Test
	public void runSyncCycle_skipsWhenFhirSyncDisabled() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		setField(service, "repository", repository);
		setField(service, "publishedConfigShrSyncGateService", new FixedShrSyncGate(false));
		
		int processed = service.runSyncCycle(10);
		
		assertEquals(0, processed);
		assertEquals(0, repository.findPendingCalls);
		assertEquals(0, repository.findFailedCalls);
	}
	
	@Test
	public void markFailed_sendsAdminAlertWhenPermanent() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		RecordingAdminAlert adminAlert = new RecordingAdminAlert();
		setField(service, "repository", repository);
		setField(service, "adminAlertService", adminAlert);
		
		IntelehealthShrSyncLog row = pendingRow(10);
		
		service.markFailed(row, null, "Max attempts exhausted", false);
		
		assertEquals(ShrSyncLogStatus.FAILED_PERMANENT, row.getStatus());
		assertEquals(1, adminAlert.alertCalls);
		assertEquals(row, adminAlert.lastRow);
	}
	
	@Test
	public void completePendingPush_schedulesRetryForHttp400() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		setField(service, "repository", repository);
		
		IntelehealthShrSyncLog row = pendingRow(1);
		FhirResponse response = new FhirResponse();
		response.setStatusCode("400");
		response.setMessage("Medication not found");
		
		service.completePendingPush(row, response, new Bundle());
		
		assertEquals(ShrSyncLogStatus.FAILED, row.getStatus());
		assertNotNull(row.getNextRetryAt());
		assertEquals(Integer.valueOf(400), row.getHttpStatusCode());
	}
	
	@Test
	public void runSyncCycle_replaysDeferredPendingAndFailedRows() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		RecordingVisitSyncPush visitSyncPush = new RecordingVisitSyncPush();
		setField(service, "repository", repository);
		setField(service, "visitSyncPush", visitSyncPush);
		setField(service, "publishedConfigShrSyncGateService", new FixedShrSyncGate(true));
		
		IntelehealthShrSyncLog deferred = pendingRow(1);
		deferred.setId(1L);
		IntelehealthShrSyncLog failed = failedRow(1);
		failed.setId(2L);
		repository.pendingRows = Collections.singletonList(deferred);
		repository.failedRows = Collections.singletonList(failed);
		
		int processed = service.runSyncCycle(10);
		
		assertEquals(2, processed);
		assertEquals(2, visitSyncPush.pushCalls);
		assertEquals(ShrSyncLogStatus.SUCCESS, deferred.getStatus());
		assertNull(failed.getNextRetryAt());
		assertEquals(ShrSyncLogStatus.SUCCESS, findSavedRetryAttempt(repository.savedRows).getStatus());
	}
	
	@Test
	public void runSyncCycle_persistsRetryAttemptWhenPushReturnsHttp500() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		setField(service, "repository", repository);
		setField(service, "visitSyncPush", new Http500VisitSyncPush(service));
		setField(service, "publishedConfigShrSyncGateService", new FixedShrSyncGate(true));
		
		IntelehealthShrSyncLog failed = failedRow(1);
		failed.setId(1L);
		repository.failedRows = Collections.singletonList(failed);
		
		int processed = service.runSyncCycle(10);
		
		assertEquals(0, processed);
		assertNull(failed.getNextRetryAt());
		IntelehealthShrSyncLog attempt2 = findSavedRetryAttempt(repository.savedRows);
		assertEquals(ShrSyncLogStatus.FAILED, attempt2.getStatus());
		assertNotNull(attempt2.getNextRetryAt());
		assertEquals(Integer.valueOf(500), attempt2.getHttpStatusCode());
	}
	
	@Test
	public void runSyncCycle_replaysLegacyFailedPermanentRows() throws Exception {
		ShrSyncLogServiceImpl service = new ShrSyncLogServiceImpl();
		TrackingRepository repository = new TrackingRepository();
		RecordingVisitSyncPush visitSyncPush = new RecordingVisitSyncPush();
		setField(service, "repository", repository);
		setField(service, "visitSyncPush", visitSyncPush);
		setField(service, "publishedConfigShrSyncGateService", new FixedShrSyncGate(true));
		
		IntelehealthShrSyncLog legacy = failedPermanentRow(1);
		legacy.setId(18L);
		repository.failedPermanentRows = Collections.singletonList(legacy);
		
		int processed = service.runSyncCycle(10);
		
		assertEquals(1, processed);
		assertEquals(ShrSyncLogStatus.FAILED, legacy.getStatus());
		assertNull(legacy.getNextRetryAt());
		IntelehealthShrSyncLog retryAttempt = findSavedRetryAttempt(repository.savedRows);
		assertEquals(2, retryAttempt.getAttemptNumber());
		assertEquals(ShrSyncLogStatus.SUCCESS, retryAttempt.getStatus());
	}
	
	private static IntelehealthShrSyncLog pendingRow(int attemptNumber) {
		IntelehealthShrSyncLog row = new IntelehealthShrSyncLog();
		row.setVisitUuid("visit-uuid");
		row.setTriggerEncounterUuid("encounter-uuid");
		row.setAttemptNumber(attemptNumber);
		row.setStatus(ShrSyncLogStatus.PENDING);
		row.setStartedAt(new Date());
		return row;
	}
	
	private static IntelehealthShrSyncLog failedRow(int attemptNumber) {
		IntelehealthShrSyncLog row = pendingRow(attemptNumber);
		row.setStatus(ShrSyncLogStatus.FAILED);
		row.setNextRetryAt(new Date(0));
		row.setCompletedAt(new Date());
		return row;
	}
	
	private static IntelehealthShrSyncLog failedPermanentRow(int attemptNumber) {
		IntelehealthShrSyncLog row = failedRow(attemptNumber);
		row.setStatus(ShrSyncLogStatus.FAILED_PERMANENT);
		row.setNextRetryAt(null);
		row.setFailureReason("HTTP 400");
		row.setHttpStatusCode(400);
		return row;
	}
	
	private static IntelehealthShrSyncLog findSavedRetryAttempt(List<IntelehealthShrSyncLog> savedRows) {
		for (IntelehealthShrSyncLog row : savedRows) {
			if (row.getAttemptNumber() == 2) {
				return row;
			}
		}
		throw new AssertionError("Expected retry attempt row with attemptNumber=2");
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
	
	private static final class RecordingAdminAlert implements ShrSyncAdminAlertContract {
		
		private int alertCalls;
		
		private IntelehealthShrSyncLog lastRow;
		
		@Override
		public void alertPermanentPushFailure(IntelehealthShrSyncLog row) {
			alertCalls++;
			lastRow = row;
		}
	}
	
	private static final class Http500VisitSyncPush implements ShrVisitSyncPushContract {
		
		private final ShrSyncLogServiceImpl service;
		
		Http500VisitSyncPush(ShrSyncLogServiceImpl service) {
			this.service = service;
		}
		
		@Override
		public boolean pushVisitForSyncLog(IntelehealthShrSyncLog pushRow, String operationLabel) {
			FhirResponse response = new FhirResponse();
			response.setStatusCode("500");
			response.setMessage("HTTP 500 Internal Server Error");
			service.completePendingPush(pushRow, response, new Bundle());
			return false;
		}
	}
	
	private static final class RecordingVisitSyncPush implements ShrVisitSyncPushContract {
		
		private int pushCalls;
		
		@Override
		public boolean pushVisitForSyncLog(IntelehealthShrSyncLog pushRow, String operationLabel) {
			pushCalls++;
			pushRow.setStatus(ShrSyncLogStatus.SUCCESS);
			return true;
		}
	}
	
	private static final class TrackingRepository extends ShrSyncLogRepository {
		
		private int findPendingCalls;
		
		private int findFailedCalls;
		
		private List<IntelehealthShrSyncLog> pendingRows = Collections.emptyList();
		
		private List<IntelehealthShrSyncLog> failedRows = Collections.emptyList();
		
		private List<IntelehealthShrSyncLog> failedPermanentRows = Collections.emptyList();
		
		private final List<IntelehealthShrSyncLog> savedRows = new ArrayList<>();
		
		@Override
		public void save(IntelehealthShrSyncLog row) {
			savedRows.add(row);
		}
		
		@Override
		public List<IntelehealthShrSyncLog> findPendingAwaitingPush(int limit) {
			findPendingCalls++;
			return pendingRows;
		}
		
		@Override
		public List<IntelehealthShrSyncLog> findFailedDueForRetry(int limit) {
			findFailedCalls++;
			return failedRows;
		}
		
		@Override
		public List<IntelehealthShrSyncLog> findFailedPermanentEligibleForRetry(int limit) {
			return failedPermanentRows;
		}
		
		@Override
		public int nextAttemptNumberForVisit(String visitUuid) {
			return 2;
		}
	}
}
