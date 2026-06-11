package org.openmrs.module.ihshr.domain;

import java.util.Date;

public class ParsedComplaint {
	
	private final String symptom;
	
	private final String sharedNoteText;
	
	private final int index;
	
	private String durationText;
	
	private Date onsetDateTime;
	
	public ParsedComplaint(String symptom, String sharedNoteText, int index) {
		this.symptom = symptom;
		this.sharedNoteText = sharedNoteText;
		this.index = index;
	}
	
	public String getSymptom() {
		return symptom;
	}
	
	public String getSharedNoteText() {
		return sharedNoteText;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getDurationText() {
		return durationText;
	}
	
	public void setDurationText(String durationText) {
		this.durationText = durationText;
	}
	
	public Date getOnsetDateTime() {
		return onsetDateTime;
	}
	
	public void setOnsetDateTime(Date onsetDateTime) {
		this.onsetDateTime = onsetDateTime;
	}
	
}
