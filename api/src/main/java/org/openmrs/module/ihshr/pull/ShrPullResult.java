package org.openmrs.module.ihshr.pull;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.pull.ShrBundleMerger.ExecutedQuery;

public class ShrPullResult {
	
	private ShrResolvedPatient patient;
	
	private ShrHistoryRequest request;
	
	private List<ExecutedQuery> executed;
	
	private Bundle merged;
	
	private boolean mergedFlag;
	
	private Map<String, String> pagination;
	
	private Integer total;
	
	private String sourceUri;
	
	private String emptyMessage;
	
	public ShrResolvedPatient getPatient() {
		return patient;
	}
	
	public void setPatient(ShrResolvedPatient patient) {
		this.patient = patient;
	}
	
	public ShrHistoryRequest getRequest() {
		return request;
	}
	
	public void setRequest(ShrHistoryRequest request) {
		this.request = request;
	}
	
	public List<ExecutedQuery> getExecuted() {
		return executed;
	}
	
	public void setExecuted(List<ExecutedQuery> executed) {
		this.executed = executed;
	}
	
	public Bundle getMerged() {
		return merged;
	}
	
	public void setMerged(Bundle merged) {
		this.merged = merged;
	}
	
	public boolean isMergedFlag() {
		return mergedFlag;
	}
	
	public void setMergedFlag(boolean mergedFlag) {
		this.mergedFlag = mergedFlag;
	}
	
	public Map<String, String> getPagination() {
		return pagination;
	}
	
	public void setPagination(Map<String, String> pagination) {
		this.pagination = pagination;
	}
	
	public Integer getTotal() {
		return total;
	}
	
	public void setTotal(Integer total) {
		this.total = total;
	}
	
	public String getSourceUri() {
		return sourceUri;
	}
	
	public void setSourceUri(String sourceUri) {
		this.sourceUri = sourceUri;
	}
	
	public String getEmptyMessage() {
		return emptyMessage;
	}
	
	public void setEmptyMessage(String emptyMessage) {
		this.emptyMessage = emptyMessage;
	}
}
