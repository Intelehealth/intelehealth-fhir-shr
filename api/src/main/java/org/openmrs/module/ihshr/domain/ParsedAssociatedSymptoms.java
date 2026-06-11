package org.openmrs.module.ihshr.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedAssociatedSymptoms {
	
	private final List<String> reports;
	
	private final List<String> denies;
	
	public ParsedAssociatedSymptoms(List<String> reports, List<String> denies) {
		this.reports = reports == null ? Collections.<String> emptyList() : new ArrayList<String>(reports);
		this.denies = denies == null ? Collections.<String> emptyList() : new ArrayList<String>(denies);
	}
	
	public List<String> getReports() {
		return reports;
	}
	
	public List<String> getDenies() {
		return denies;
	}
	
	public boolean isEmpty() {
		return reports.isEmpty() && denies.isEmpty();
	}
	
}
