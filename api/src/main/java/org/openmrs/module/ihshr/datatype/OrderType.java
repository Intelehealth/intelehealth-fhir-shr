package org.openmrs.module.ihshr.datatype;

public enum OrderType {
	
	DRUG_ORDER(2), LAB_ORDER(3), RADIOLOGY_ORDER(4);
	
	private int type;
	
	OrderType(int value) {
		this.type = value;
	}
	
	public int getValue() {
		return this.type;
	}
}
