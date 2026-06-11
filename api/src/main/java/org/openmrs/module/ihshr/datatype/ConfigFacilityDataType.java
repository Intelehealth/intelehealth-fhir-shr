package org.openmrs.module.ihshr.datatype;

//Registration of Patients
//Health Record Exchange
//Scheduling Appointments
//Data Exchange with Diagnostic Centres
//Prescription Sharing
//Referral

public enum ConfigFacilityDataType {
	
	PATIENTS(1), HEALTH_RECORD(2), // Patient Service
	APPOINTMENT(3), DIAGNOSTIC(4), // Lab
	PRESCRIPTION_SHARING(5), // Medication
	REFERRAL(6);
	
	private int value;
	
	private ConfigFacilityDataType(int type) {
		this.value = type;
	}
	
	public int getValue() {
		return value;
	}
	
}
