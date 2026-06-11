package org.openmrs.module.ihshr.exp;

public class InvalidParamException extends RuntimeException {
	
	public InvalidParamException() {
		super();
	}
	
	public InvalidParamException(String msg) {
		super(msg);
	}
	
}
