package org.openmrs.module.ihshr.domain;

public class LocationInfo {
	
	private String patientId;
	
	private String identifier;
	
	private String identifierType;
	
	private String locationUUID;
	
	private String locationName;
	
	private String locationId;
	
	public String getPatientId() {
		return patientId;
	}
	
	public void setPatientId(String patientId) {
		this.patientId = patientId;
	}
	
	public String getIdentifier() {
		return identifier;
	}
	
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	
	public String getIdentifierType() {
		return identifierType;
	}
	
	public void setIdentifierType(String identifierType) {
		this.identifierType = identifierType;
	}
	
	public String getLocationUUID() {
		return locationUUID;
	}
	
	public void setLocationUUID(String locationUUID) {
		this.locationUUID = locationUUID;
	}
	
	public String getLocationName() {
		return locationName;
	}
	
	public void setLocationName(String locationName) {
		this.locationName = locationName;
	}
	
	public String getLocationId() {
		return locationId;
	}
	
	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}
	
	@Override
	public String toString() {
		return "LocationInfo [patientId=" + patientId + ", identifier=" + identifier + ", identifierType=" + identifierType
		        + ", locationUUID=" + locationUUID + ", locationName=" + locationName + ", locationId=" + locationId + "]";
	}
	
}
