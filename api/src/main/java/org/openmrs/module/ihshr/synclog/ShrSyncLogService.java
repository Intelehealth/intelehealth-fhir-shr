package org.openmrs.module.ihshr.synclog;

import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.domain.FhirResponse;

/**
 * SHR push sync log lifecycle (doc §3.1 steps 9/16, §3.2, §13).
 */
public interface ShrSyncLogService {
	
	IntelehealthShrSyncLog createPending(ObsPushContext context, String requestBundleJson);
	
	void markSuccess(IntelehealthShrSyncLog row, FhirResponse response, Bundle requestBundle, Bundle responseBundle);
	
	void markFailed(IntelehealthShrSyncLog row, FhirResponse response, String failureReason, boolean permanent);
	
	/**
	 * Step 16 (SUCCESS) or §3.2 failure handling for a PENDING row after the SHR HTTP call.
	 * 
	 * @throws IllegalStateException when the push did not succeed
	 */
	void completePendingPush(IntelehealthShrSyncLog pending, FhirResponse response, Bundle requestBundle);
	
	/**
	 * Immediate push for a {@link ShrSyncLogStatus#PENDING} row created during visit export (FHIR
	 * sync enabled). On failure the row is scheduled for {@link #runSyncCycle(int)}.
	 */
	void tryPushPendingRow(IntelehealthShrSyncLog pending, Bundle requestBundle);
	
	void refreshRequestBundle(IntelehealthShrSyncLog row, Bundle requestBundle);
	
	/**
	 * Replays every recoverable sync-log row: deferred {@code PENDING} (never posted),
	 * {@code FAILED} due for backoff retry, and legacy {@code FAILED_PERMANENT} rows that still
	 * have attempts left.
	 */
	int runSyncCycle(int limitPerCycle);
	
	int runRetryCycle(int limitPerCycle);
	
	int runPendingPushCycle(int limitPerCycle);
}
