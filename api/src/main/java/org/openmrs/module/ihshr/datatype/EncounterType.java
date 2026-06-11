package org.openmrs.module.ihshr.datatype;

public enum EncounterType {
	
	ADULTINITIAL(1), ADULTRETURN(2), PEDSINITIAL(3), PEDSRETURN(4), CHECK_IN(5), VITALS(6), DISCHARGE(7), ADMISSION(8), VISIT_NOTE(
	        9), CHECK_OUT(10), TRANSFER(11), PATIENT_EXIT_SURVEY(12), RHPT_INTERPRETATION(13), VISIT_COMPLETE(14), FLAGGED(
	        15), ATTACHMENT_UPLOAD(16), ORDER(17);
	
	private int value;
	
	EncounterType(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return this.value;
	}
	
}
