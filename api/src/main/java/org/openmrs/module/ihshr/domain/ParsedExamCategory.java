package org.openmrs.module.ihshr.domain;

import java.util.Collections;
import java.util.List;

public class ParsedExamCategory {
	
	private final String categoryName;
	
	private final List<ParsedFinding> findings;
	
	public ParsedExamCategory(String categoryName, List<ParsedFinding> findings) {
		this.categoryName = categoryName;
		this.findings = findings == null ? Collections.<ParsedFinding> emptyList() : findings;
	}
	
	public String getCategoryName() {
		return categoryName;
	}
	
	public List<ParsedFinding> getFindings() {
		return findings;
	}
	
}
