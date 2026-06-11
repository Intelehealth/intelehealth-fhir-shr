package org.openmrs.module.ihshr.domain;

public class ParsedDiagnosis {
	
	private String code;
	
	private String diagnosisText;
	
	private String diagnosisType;
	
	private String diagnosisCategory;
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public String getDiagnosisText() {
		return diagnosisText;
	}
	
	public void setDiagnosisText(String diagnosisText) {
		this.diagnosisText = diagnosisText;
	}
	
	public String getDiagnosisType() {
		return diagnosisType;
	}
	
	public void setDiagnosisType(String diagnosisType) {
		this.diagnosisType = diagnosisType;
	}
	
	public String getDiagnosisCategory() {
		return diagnosisCategory;
	}
	
	public void setDiagnosisCategory(String diagnosisCategory) {
		this.diagnosisCategory = diagnosisCategory;
	}
}
