package org.openmrs.module.ihshr.domain;

public class CompletedRecord {
	
	private Integer id;
	
	private String uuid;
	
	private String dateCreated;
	
	private Integer conceptId;
	
	private Integer encounterId;
	
	private String valueText;
	
	private String valueComplex;
	
	private String comments;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public String getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(String dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Integer getConceptId() {
		return conceptId;
	}
	
	public void setConceptId(Integer conceptId) {
		this.conceptId = conceptId;
	}
	
	public Integer getEncounterId() {
		return encounterId;
	}
	
	public void setEncounterId(Integer encounterId) {
		this.encounterId = encounterId;
	}
	
	public String getValueText() {
		return valueText;
	}
	
	public void setValueText(String valueText) {
		this.valueText = valueText;
	}
	
	public String getValueComplex() {
		return valueComplex;
	}
	
	public void setValueComplex(String valueComplex) {
		this.valueComplex = valueComplex;
	}
	
	public String getComments() {
		return comments;
	}
	
	public void setComments(String comments) {
		this.comments = comments;
	}
	
}
