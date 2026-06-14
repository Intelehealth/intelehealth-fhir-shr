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
			if (pushStoredBundle(row, row, "deferred pending push")) {
				processed++;
			}
		}
		return processed;
	}
	
	private int replayFailedRows(int limit) {
		Set<Long> seenIds = new LinkedHashSet<>();
		List<IntelehealthShrSyncLog> due = new ArrayList<>();
		for (IntelehealthShrSyncLog row : repository.findFailedDueForRetry(limit)) {
			if (seenIds.add(row.getId())) {
				due.add(row);
			}
		}
		int remaining = Math.max(0, limit - due.size());
		if (remaining > 0) {
			for (IntelehealthShrSyncLog row : repository.findFailedPermanentEligibleForRetry(remaining)) {
				if (seenIds.add(row.getId())) {
					due.add(row);
				}
			}
		}
		int processed = 0;
		for (IntelehealthShrSyncLog failed : due) {
			IntelehealthShrSyncLog attempt = null;
			try {
				supersedeRetrySource(failed);
				attempt = startRetryAttempt(failed);
				if (pushStoredBundle(attempt, failed, "sync retry")) {
					processed++;
				}
			}
			catch (Exception ex) {
				LOG.error("SHR sync retry failed for log id {}: {}", failed.getId(), ex.getMessage(), ex);
				IntelehealthShrSyncLog row = attempt != null ? attempt : failed;
				markFailed(row, null, ex.getMessage(), false);
			}
		}
		return processed;
	}
	
	private boolean pushStoredBundle(IntelehealthShrSyncLog pushRow, IntelehealthShrSyncLog logContext, String operationLabel) {
		if (visitSyncPush == null) {
			LOG.error("shrVisitSyncPush not available; cannot replay visit push for log id {}", pushRow.getId());
			markFailed(pushRow, null, "Visit sync push service not available", false);
			return false;
		}
		if (StringUtils.isBlank(pushRow.getVisitUuid())) {
			markFailed(pushRow, null, "Missing visit UUID for " + operationLabel, true);
			return false;
		}
		return visitSyncPush.pushVisitForSyncLog(pushRow, operationLabel);
	}
	
	private IntelehealthShrSyncLog startRetryAttempt(IntelehealthShrSyncLog failed) {
		IntelehealthShrSyncLog attempt = new IntelehealthShrSyncLog();
		attempt.setVisitUuid(failed.getVisitUuid());
		attempt.setTriggerEncounterUuid(failed.getTriggerEncounterUuid());
		attempt.setAttemptNumber(failed.getAttemptNumber() + 1);
		attempt.setRequestBundle(null);
		attempt.setStatus(ShrSyncLogStatus.PENDING);
		Date now = new Date();
		attempt.setStartedAt(now);
		attempt.setCreatedAt(now);
		attempt.setUpdatedAt(now);
		repository.save(attempt);
		return attempt;
	}
	
	private void notifyPermanentFailure(IntelehealthShrSyncLog row) {
		if (adminAlertService != null) {
			adminAlertService.alertPermanentPushFailure(row);
		}
	}
	
	private void supersedeRetrySource(IntelehealthShrSyncLog source) {
		source.setNextRetryAt(null);
		if (source.getStatus() == ShrSyncLogStatus.FAILED_PERMANENT) {
			source.setStatus(ShrSyncLogStatus.FAILED);
		}
		source.setUpdatedAt(new Date());
		repository.save(source);
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
