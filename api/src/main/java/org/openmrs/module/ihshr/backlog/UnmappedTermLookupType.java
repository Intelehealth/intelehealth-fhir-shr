package org.openmrs.module.ihshr.backlog;

public enum UnmappedTermLookupType {
	
	MAPPING,
	
	CATEGORY;
	
	public String code() {
		return name().toLowerCase();
	}
	
}
