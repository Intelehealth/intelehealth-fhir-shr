package org.openmrs.module.ihshr.pull;

public final class ShrFhirQuery {
	
	private final String queryId;
	
	private final String resourceType;
	
	private final String url;
	
	public ShrFhirQuery(String queryId, String resourceType, String url) {
		this.queryId = queryId;
		this.resourceType = resourceType;
		this.url = url;
	}
	
	public String getQueryId() {
		return queryId;
	}
	
	public String getResourceType() {
		return resourceType;
	}
	
	public String getUrl() {
		return url;
	}
}
