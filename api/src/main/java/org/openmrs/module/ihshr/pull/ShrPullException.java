package org.openmrs.module.ihshr.pull;

public class ShrPullException extends RuntimeException {
	
	private final ShrPullErrorCode errorCode;
	
	public ShrPullException(ShrPullErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
	
	public ShrPullException(ShrPullErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}
	
	public ShrPullErrorCode getErrorCode() {
		return errorCode;
	}
}
