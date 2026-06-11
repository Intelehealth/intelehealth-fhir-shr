package org.openmrs.module.ihshr.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedFamilyHistoryRelative {
	
	private final String relativeLabel;
	
	private final String roleCode;
	
	private final String mapKey;
	
	private final List<String> conditions = new ArrayList<String>();
	
	public ParsedFamilyHistoryRelative(String relativeLabel, String roleCode, String mapKey) {
		this.relativeLabel = relativeLabel;
		this.roleCode = roleCode;
		this.mapKey = mapKey;
	}
	
	public void addCondition(String condition) {
		if (condition != null && !condition.isEmpty()) {
			conditions.add(condition);
		}
	}
	
	public String getRelativeLabel() {
		return relativeLabel;
	}
	
	public String getRoleCode() {
		return roleCode;
	}
	
	public String getMapKey() {
		return mapKey;
	}
	
	public List<String> getConditions() {
		return Collections.unmodifiableList(conditions);
	}
	
}
