package org.openmrs.module.ihshr.domain;

public class FhirResponse {
	
	private String response;
	
	private String statusCode;
	
	private String message;
	
	public String getResponse() {
		return response;
	}
	
	public void setResponse(String response) {
		this.response = response;
	}
	
	public String getStatusCode() {
		return statusCode;
	}
	
	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	@Override
	public String toString() {
		return "HttpResponse [response=" + response + ", statusCode=" + statusCode + ", message=" + message + "]";
	}
	
}
