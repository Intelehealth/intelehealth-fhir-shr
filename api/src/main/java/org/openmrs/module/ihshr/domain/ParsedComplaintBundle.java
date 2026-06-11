package org.openmrs.module.ihshr.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedComplaintBundle {
	
	private final List<ParsedComplaint> complaints;
	
	private final ParsedAssociatedSymptoms associatedSymptoms;
	
	private final String sharedNoteText;
	
	public ParsedComplaintBundle(List<ParsedComplaint> complaints, ParsedAssociatedSymptoms associatedSymptoms,
	    String sharedNoteText) {
		this.complaints = complaints == null ? Collections.<ParsedComplaint> emptyList() : new ArrayList<ParsedComplaint>(
		        complaints);
		this.associatedSymptoms = associatedSymptoms;
		this.sharedNoteText = sharedNoteText == null ? "" : sharedNoteText;
	}
	
	public List<ParsedComplaint> getComplaints() {
		return complaints;
	}
	
	public ParsedAssociatedSymptoms getAssociatedSymptoms() {
		return associatedSymptoms;
	}
	
	public String getSharedNoteText() {
		return sharedNoteText;
	}
	
}
