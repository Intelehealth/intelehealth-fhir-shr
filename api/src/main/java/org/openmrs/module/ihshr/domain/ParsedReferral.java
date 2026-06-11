package org.openmrs.module.ihshr.domain;

public class ParsedReferral {
	
	private String specialty;
	
	private String category;
	
	private String priorityText;
	
	private String notes;
	
	private int index;
	
	public String getSpecialty() {
		return specialty;
	}
	
	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}
	
	public String getCategory() {
		return category;
	}
	
	public void setCategory(String category) {
		this.category = category;
	}
	
	public String getPriorityText() {
		return priorityText;
	}
	
	public void setPriorityText(String priorityText) {
		this.priorityText = priorityText;
	}
	
	public String getNotes() {
		return notes;
	}
	
	public void setNotes(String notes) {
		this.notes = notes;
	}
	
	public int getIndex() {
		return index;
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
}
