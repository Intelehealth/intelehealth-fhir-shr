package org.openmrs.module.ihshr.domain;

public class ParsedFinding {
	
	private final String item;
	
	private final String finding;
	
	public ParsedFinding(String item, String finding) {
		this.item = item;
		this.finding = finding;
	}
	
	public String getItem() {
		return item;
	}
	
	public String getFinding() {
		return finding;
	}
	
}
