package org.openmrs.module.ihshr.pull;

public class ShrResolvedPatient {
	
	private String openmrsPatientUuid;
	
	private String openmrsPatientDisplay;
	
	private String cruid;
	
	private boolean shrPatientFound;
	
	private String shrPatientId;
	
	private String shrPatientDisplay;
	
	public String getOpenmrsPatientUuid() {
		return openmrsPatientUuid;
	}
	
	public void setOpenmrsPatientUuid(String openmrsPatientUuid) {
		this.openmrsPatientUuid = openmrsPatientUuid;
	}
	
	public String getOpenmrsPatientDisplay() {
		return openmrsPatientDisplay;
	}
	
	public void setOpenmrsPatientDisplay(String openmrsPatientDisplay) {
		this.openmrsPatientDisplay = openmrsPatientDisplay;
	}
	
	public String getCruid() {
		return cruid;
	}
	
	public void setCruid(String cruid) {
		this.cruid = cruid;
	}
	
	public boolean isShrPatientFound() {
		return shrPatientFound;
	}
	
	public void setShrPatientFound(boolean shrPatientFound) {
		this.shrPatientFound = shrPatientFound;
	}
	
	public String getShrPatientId() {
		return shrPatientId;
	}
	
	public void setShrPatientId(String shrPatientId) {
		this.shrPatientId = shrPatientId;
	}
	
	public String getShrPatientDisplay() {
		return shrPatientDisplay;
	}
	
	public void setShrPatientDisplay(String shrPatientDisplay) {
		this.shrPatientDisplay = shrPatientDisplay;
	}
}
