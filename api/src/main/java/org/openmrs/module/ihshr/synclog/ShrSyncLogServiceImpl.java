package org.openmrs.module.ihshr.synclog;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihmodule.api.patientexchange.config.FhirContextHolder;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.config.PublishedConfigShrSyncGateService;
import org.openmrs.module.ihshr.domain.FhirResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("shrSyncLogService")
public class ShrSyncLogServiceImpl implements ShrSyncLogService {
	
	private static final Logger LOG = LoggerFactory.getLogger(ShrSyncLogServiceImpl.class);
	
	@Autowired
	private ShrSyncLogRepository repository;
	
	@Autowired
	private ShrVisitPushStatusServiceContract visitPushStatusService;
	
	@Autowired
	private FhirConfig fhirConfig;
	
	@Autowired
	private PublishedConfigShrSyncGateService publishedConfigShrSyncGateService;
	
	@Autowired(required = false)
	private ShrVisitSyncPushContract visitSyncPush;
	
	@Autowired(required = false)
	private ShrSyncAdminAlertContract adminAlertService;
	
	@Autowired
	@Lazy
	private ShrSyncLogRetryRunner retryRunner;
	
	@Override
	@Transactional
	public IntelehealthShrSyncLog createPending(ObsPushContext context, String requestBundleJson) {
		IntelehealthShrSyncLog row = new IntelehealthShrSyncLog();
		row.setVisitUuid(context.getVisitUuid());
		row.setTriggerEncounterUuid(context.getTriggerEncounterUuid());
		row.setAttemptNumber(repository.nextAttemptNumberForVisit(context.getVisitUuid()));
		row.setRequestBundle(ShrSyncLogCompression.gzip(requestBundleJson));
		row.setStatus(ShrSyncLogStatus.PENDING);
		row.setStartedAt(new Date());
		row.setCreatedAt(new Date());
		row.setUpdatedAt(new Date());
		repository.save(row);
		return row;
	}
	
	@Override
	@Transactional
	public void markSuccess(IntelehealthShrSyncLog row, FhirResponse response, Bundle requestBundle, Bundle responseBundle) {
		row.setStatus(ShrSyncLogStatus.SUCCESS);
		row.setCompletedAt(new Date());
		row.setNextRetryAt(null);
		row.setFailureReason(null);
		row.setHttpStatusCode(parseStatusCode(response));
		if (response != null && StringUtils.isNotBlank(response.getResponse())) {
			row.setResponseBundle(ShrSyncLogCompression.gzip(response.getResponse()));
		}
		if (responseBundle != null) {
			row.setResourceMap(ShrSyncResourceMapBuilder.buildFromResponseBundle(responseBundle, requestBundle));
			if (responseBundle.hasIdElement() && responseBundle.getIdElement().hasIdPart()) {
				row.setShrBundleId(responseBundle.getIdElement().getIdPart());
			}
		}
		row.setUpdatedAt(new Date());
		repository.save(row);
		visitPushStatusService.recordSuccess(row.getVisitUuid());
	}
	
	@Override
	@Transactional
	public void markFailed(IntelehealthShrSyncLog row, FhirResponse response, String failureReason, boolean permanent) {
		row.setCompletedAt(new Date());
		row.setHttpStatusCode(parseStatusCode(response));
		row.setFailureReason(truncate(failureReason));
		if (response != null && StringUtils.isNotBlank(response.getResponse())) {
			row.setResponseBundle(ShrSyncLogCompression.gzip(response.getResponse()));
		}
		if (permanent || !ShrSyncRetryPolicy.canRetry(row.getAttemptNumber())) {
			row.setStatus(ShrSyncLogStatus.FAILED_PERMANENT);
			row.setNextRetryAt(null);
			LOG.error("SHR push permanently failed for visit {} attempt {}: {}", row.getVisitUuid(), row.getAttemptNumber(),
			    row.getFailureReason());
			notifyPermanentFailure(row);
		} else {
			row.setStatus(ShrSyncLogStatus.FAILED);
			row.setNextRetryAt(ShrSyncRetryPolicy.computeNextRetryAt(row.getAttemptNumber()));
			LOG.warn("SHR push failed for visit {} attempt {}; next retry at {}", row.getVisitUuid(),
			    row.getAttemptNumber(), row.getNextRetryAt());
		}
		row.setUpdatedAt(new Date());
		repository.save(row);
	}
	
	@Override
	@Transactional
	public void completePendingPush(IntelehealthShrSyncLog pending, FhirResponse response, Bundle requestBundle) {
		if (isSuccess(response)) {
			Bundle responseBundle = null;
			if (response != null && StringUtils.isNotBlank(response.getResponse())) {
				responseBundle = FhirContextHolder.R4.newJsonParser().parseResource(Bundle.class, response.getResponse());
			}
			markSuccess(pending, response, requestBundle, responseBundle);
			return;
		}
		String reason = response != null && StringUtils.isNotBlank(response.getMessage()) ? response.getMessage()
		        : "SHR push failed";
		markFailed(pending, response, reason, false);
	}
	
	@Override
	@Transactional
	public void refreshRequestBundle(IntelehealthShrSyncLog row, Bundle requestBundle) {
		String payload = FhirContextHolder.R4.newJsonParser().setPrettyPrint(true).encodeResourceToString(requestBundle);
		row.setRequestBundle(ShrSyncLogCompression.gzip(payload));
		row.setUpdatedAt(new Date());
		repository.save(row);
	}
	
	@Override
	@Transactional
	public void tryPushPendingRow(IntelehealthShrSyncLog pending, Bundle requestBundle) {
		try {
			FhirResponse response = fhirConfig.postTransactionBundle(requestBundle);
			LOG.info("SHR sync log id={} immediate push status={}", pending.getId(),
			    response != null ? response.getStatusCode() : "null");
			completePendingPush(pending, response, requestBundle);
		}
		catch (Exception ex) {
			markFailed(pending, null, ex.getMessage(), false);
		}
	}
	
	@Override
	@Transactional
	/**
	 * Executes one scheduler sync cycle for SHR push logs.
	 * <p>
	 * Order is intentional:
	 * <ol>
	 * <li>Push rows currently in {@code PENDING_AWAITING_PUSH}</li>
	 * <li>Use any remaining slot capacity to replay {@code FAILED} rows</li>
	 * </ol>
	 * The gate {@code publishedConfigShrSyncGateService.isShrSyncEnabled()} must be true,
	 * otherwise nothing is processed.
	 * 
	 * @param limitPerCycle max rows to process in this cycle (minimum effective value is 1)
	 * @return total rows processed across pending-push + failed-replay steps
	 */
	public int runSyncCycle(int limitPerCycle) {
		if (!publishedConfigShrSyncGateService.isShrSyncEnabled()) {
			return 0;
		}
		int remaining = Math.max(1, limitPerCycle);
		int processed = 0;
		processed += pushDeferredPendingRows(remaining);
		remaining = Math.max(0, limitPerCycle - processed);
		if (remaining > 0) {
			processed += replayFailedRows(remaining);
		}
		return processed;
	}
	
	@Override
	@Transactional
	public int runRetryCycle(int limitPerCycle) {
		return replayFailedRows(limitPerCycle);
	}
	
	@Override
	@Transactional
	public int runPendingPushCycle(int limitPerCycle) {
		if (!publishedConfigShrSyncGateService.isShrSyncEnabled()) {
			return 0;
		}
		return pushDeferredPendingRows(limitPerCycle);
	}
	
	private int pushDeferredPendingRows(int limit) {
		List<IntelehealthShrSyncLog> pending = repository.findPendingAwaitingPush(limit);
		int processed = 0;
		for (IntelehealthShrSyncLog row : pending) {
			pushStoredBundle(row, "deferred pending push");
			if (isTerminalStatus(row.getStatus())) {
				processed++;
			}
		}
		return processed;
	}
	
	private int replayFailedRows(int limit) {
		Set<Long> seenIds = new LinkedHashSet<>();
		Set<String> seenVisitUuids = new LinkedHashSet<>();
		List<IntelehealthShrSyncLog> due = new ArrayList<>();
		for (IntelehealthShrSyncLog row : repository.findFailedDueForRetry(limit)) {
			if (seenIds.add(row.getId()) && seenVisitUuids.add(row.getVisitUuid())) {
				due.add(row);
			}
		}
		int remaining = Math.max(0, limit - due.size());
		if (remaining > 0) {
			for (IntelehealthShrSyncLog row : repository.findFailedPermanentEligibleForRetry(remaining)) {
				if (seenIds.add(row.getId()) && seenVisitUuids.add(row.getVisitUuid())) {
					due.add(row);
				}
			}
		}
		int processed = 0;
		for (IntelehealthShrSyncLog failed : due) {
			if (retryRunner.replayOne(failed)) {
				processed++;
			}
		}
		return processed;
	}
	
	/**
	 * Retries one failed sync-log row (called inside {@link ShrSyncLogRetryRunner}'s isolated
	 * transaction).
	 */
	@Override
	public boolean replaySingleFailedRow(IntelehealthShrSyncLog failed) {
		IntelehealthShrSyncLog attempt = null;
		try {
			supersedeRetrySource(failed);
			attempt = resolveRetryAttemptRow(failed);
			pushStoredBundle(attempt, "sync retry");
			return isTerminalStatus(attempt.getStatus());
		}
		catch (Exception ex) {
			LOG.error("SHR sync retry failed for log id {} visit {}: {}", failed.getId(), failed.getVisitUuid(),
			    ex.getMessage(), ex);
			if (attempt != null && attempt.getId() != null) {
				markFailed(attempt, null, ex.getMessage(), false);
				return isTerminalStatus(attempt.getStatus());
			}
			if (failed.getId() != null) {
				markFailed(failed, null, ex.getMessage(), false);
				return isTerminalStatus(failed.getStatus());
			}
			return false;
		}
		finally {
			if (attempt != null && attempt.getId() == null) {
				repository.evict(attempt);
			}
		}
	}
	
	private IntelehealthShrSyncLog resolveRetryAttemptRow(IntelehealthShrSyncLog failed) {
		String visitUuid = failed.getVisitUuid();
		IntelehealthShrSyncLog openPending = repository.findOpenPendingForVisit(visitUuid);
		if (openPending != null) {
			LOG.info("Reusing open PENDING sync log id={} attempt={} for visit {}", openPending.getId(),
			    openPending.getAttemptNumber(), visitUuid);
			return openPending;
		}
		int nextAttempt = repository.nextAttemptNumberForVisit(visitUuid);
		IntelehealthShrSyncLog existing = repository.findByVisitAndAttempt(visitUuid, nextAttempt);
		if (existing != null) {
			LOG.warn("Sync log visit={} attempt={} already exists (id={}, status={}); reusing row", visitUuid, nextAttempt,
			    existing.getId(), existing.getStatus());
			return existing;
		}
		return createRetryAttempt(failed, nextAttempt);
	}
	
	private IntelehealthShrSyncLog createRetryAttempt(IntelehealthShrSyncLog failed, int attemptNumber) {
		IntelehealthShrSyncLog attempt = new IntelehealthShrSyncLog();
		attempt.setVisitUuid(failed.getVisitUuid());
		attempt.setTriggerEncounterUuid(failed.getTriggerEncounterUuid());
		attempt.setAttemptNumber(attemptNumber);
		attempt.setRequestBundle(null);
		attempt.setStatus(ShrSyncLogStatus.PENDING);
		Date now = new Date();
		attempt.setStartedAt(now);
		attempt.setCreatedAt(now);
		attempt.setUpdatedAt(now);
		repository.save(attempt);
		return attempt;
	}
	
	private void pushStoredBundle(IntelehealthShrSyncLog pushRow, String operationLabel) {
		if (visitSyncPush == null) {
			LOG.error("shrVisitSyncPush not available; cannot replay visit push for log id {}", pushRow.getId());
			markFailed(pushRow, null, "Visit sync push service not available", false);
			return;
		}
		if (StringUtils.isBlank(pushRow.getVisitUuid())) {
			markFailed(pushRow, null, "Missing visit UUID for " + operationLabel, true);
			return;
		}
		visitSyncPush.pushVisitForSyncLog(pushRow, operationLabel);
		finalizeAttemptOutcome(pushRow);
	}
	
	/**
	 * Closes orphan {@code PENDING} rows when a push delegate returns {@code false} without calling
	 * {@link #markSuccess} / {@link #markFailed} (otherwise {@link #findPendingAwaitingPush}
	 * retries them forever).
	 */
	private void ensurePendingAttemptRecorded(IntelehealthShrSyncLog row, String reason) {
		if (row == null || row.getStatus() != ShrSyncLogStatus.PENDING || row.getCompletedAt() != null) {
			return;
		}
		markFailed(row, null, reason, false);
	}
	
	private void notifyPermanentFailure(IntelehealthShrSyncLog row) {
		if (adminAlertService != null) {
			adminAlertService.alertPermanentPushFailure(row);
		}
	}
	
	private void supersedeRetrySource(IntelehealthShrSyncLog source) {
		source.setNextRetryAt(null);
		if (source.getStatus() == ShrSyncLogStatus.FAILED || source.getStatus() == ShrSyncLogStatus.FAILED_PERMANENT) {
			source.setStatus(ShrSyncLogStatus.SUPERSEDED);
		}
		source.setUpdatedAt(new Date());
		repository.save(source);
	}
	
	/**
	 * Ensures the attempt row left {@link ShrSyncLogStatus#PENDING} after push (success or
	 * failure).
	 */
	private void finalizeAttemptOutcome(IntelehealthShrSyncLog attempt) {
		if (attempt == null) {
			return;
		}
		if (attempt.getId() != null) {
			IntelehealthShrSyncLog persisted = repository.findById(attempt.getId());
			if (persisted != null) {
				copyOutcomeFields(persisted, attempt);
			}
		}
		if (isTerminalStatus(attempt.getStatus())) {
			LOG.info("SHR sync log id={} visit={} attempt={} outcome={}", attempt.getId(), attempt.getVisitUuid(),
			    attempt.getAttemptNumber(), attempt.getStatus());
			return;
		}
		ensurePendingAttemptRecorded(attempt, "SHR visit push completed without recording sync log outcome");
	}
	
	static void copyOutcomeFields(IntelehealthShrSyncLog from, IntelehealthShrSyncLog to) {
		to.setStatus(from.getStatus());
		to.setCompletedAt(from.getCompletedAt());
		to.setNextRetryAt(from.getNextRetryAt());
		to.setFailureReason(from.getFailureReason());
		to.setHttpStatusCode(from.getHttpStatusCode());
		to.setResponseBundle(from.getResponseBundle());
		to.setResourceMap(from.getResourceMap());
		to.setShrBundleId(from.getShrBundleId());
		to.setUpdatedAt(from.getUpdatedAt());
	}
	
	private static boolean isTerminalStatus(ShrSyncLogStatus status) {
		return status == ShrSyncLogStatus.SUCCESS || status == ShrSyncLogStatus.FAILED
		        || status == ShrSyncLogStatus.FAILED_PERMANENT;
	}
	
	static boolean isSuccess(FhirResponse response) {
		return response != null && "200".equals(response.getStatusCode());
	}
	
	private static Integer parseStatusCode(FhirResponse response) {
		if (response == null || StringUtils.isBlank(response.getStatusCode())) {
			return null;
		}
		try {
			return Integer.valueOf(response.getStatusCode());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}
	
	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= 4000 ? message : message.substring(0, 4000) + "...";
	}
}
