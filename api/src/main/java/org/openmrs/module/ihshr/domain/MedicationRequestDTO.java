package org.openmrs.module.ihshr.domain;

import java.math.BigDecimal;

public class MedicationRequestDTO {
	
	private String medicationName;
	
	private String dosage;
	
	private BigDecimal days;
	
	private String doctorName;
	
	public String getMedicationName() {
		return medicationName;
	}
	
	public void setMedicationName(String medicationName) {
		this.medicationName = medicationName;
	}
	
	public String getDosage() {
		return dosage;
	}
	
	public void setDosage(String dosage) {
		this.dosage = dosage;
	}
	
	public BigDecimal getDays() {
		return days;
	}
	
	public void setDays(BigDecimal days) {
		this.days = days;
	}
	
	public String getDoctorName() {
		return doctorName;
	}
	
	public void setDoctorName(String doctorName) {
		this.doctorName = doctorName;
	}
	
	@Override
	public String toString() {
		return "MedicationRequestDTO [medicationName=" + medicationName + ", dosage=" + dosage + ", days=" + days
		        + ", doctorName=" + doctorName + "]";
	}
	
}
