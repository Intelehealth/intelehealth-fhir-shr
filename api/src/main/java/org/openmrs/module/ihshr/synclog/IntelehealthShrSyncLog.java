package org.openmrs.module.ihshr.synclog;

import java.util.Date;

/**
 * Row in {@code intelehealth_shr_sync_log} (doc §13.1).
 */
public class IntelehealthShrSyncLog {
	
	private Long id;
	
	private String visitUuid;
	
	private String triggerEncounterUuid;
	
	private int attemptNumber = 1;
	
	private byte[] requestBundle;
	
	private byte[] responseBundle;
	
	private String shrBundleId;
	
	private ShrSyncLogStatus status;
	
	private String failureReason;
	
	private Integer httpStatusCode;
	
	private Date startedAt = new Date();
	
	private Date completedAt;
	
	private Date nextRetryAt;
	
	private String resourceMap;
	
	private Date createdAt = new Date();
	
	private Date updatedAt = new Date();
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getVisitUuid() {
		return visitUuid;
	}
	
	public void setVisitUuid(String visitUuid) {
		this.visitUuid = visitUuid;
	}
	
	public String getTriggerEncounterUuid() {
		return triggerEncounterUuid;
	}
	
	public void setTriggerEncounterUuid(String triggerEncounterUuid) {
		this.triggerEncounterUuid = triggerEncounterUuid;
	}
	
	public int getAttemptNumber() {
		return attemptNumber;
	}
	
	public void setAttemptNumber(int attemptNumber) {
		this.attemptNumber = attemptNumber;
	}
	
	public byte[] getRequestBundle() {
		return requestBundle;
	}
	
	public void setRequestBundle(byte[] requestBundle) {
		this.requestBundle = requestBundle;
	}
	
	public byte[] getResponseBundle() {
		return responseBundle;
	}
	
	public void setResponseBundle(byte[] responseBundle) {
		this.responseBundle = responseBundle;
	}
	
	public String getShrBundleId() {
		return shrBundleId;
	}
	
	public void setShrBundleId(String shrBundleId) {
		this.shrBundleId = shrBundleId;
	}
	
	public ShrSyncLogStatus getStatus() {
		return status;
	}
	
	public void setStatus(ShrSyncLogStatus status) {
		this.status = status;
	}
	
	public String getFailureReason() {
		return failureReason;
	}
	
	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}
	
	public Integer getHttpStatusCode() {
		return httpStatusCode;
	}
	
	public void setHttpStatusCode(Integer httpStatusCode) {
		this.httpStatusCode = httpStatusCode;
	}
	
	public Date getStartedAt() {
		return startedAt;
	}
	
	public void setStartedAt(Date startedAt) {
		this.startedAt = startedAt;
	}
	
	public Date getCompletedAt() {
		return completedAt;
	}
	
	public void setCompletedAt(Date completedAt) {
		this.completedAt = completedAt;
	}
	
	public Date getNextRetryAt() {
		return nextRetryAt;
	}
	
	public void setNextRetryAt(Date nextRetryAt) {
		this.nextRetryAt = nextRetryAt;
	}
	
	public String getResourceMap() {
		return resourceMap;
	}
	
	public void setResourceMap(String resourceMap) {
		this.resourceMap = resourceMap;
	}
	
	public Date getCreatedAt() {
		return createdAt;
	}
	
	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}
	
	public Date getUpdatedAt() {
		return updatedAt;
	}
	
	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}
}
