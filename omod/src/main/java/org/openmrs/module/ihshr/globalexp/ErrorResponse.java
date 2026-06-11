package org.openmrs.module.ihshr.globalexp;

import java.time.LocalDateTime;
import java.util.*;

public class ErrorResponse {

	private LocalDateTime timestamp = LocalDateTime.now();
	private int status;
	private String message;
	private String error;
	private String path;
	private List<HashMap<String, String>> errors = new ArrayList<>();

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public List<HashMap<String, String>> getErrors() {
		return errors;
	}

	public void setErrors(List<HashMap<String, String>> errors) {
		this.errors = errors;
	}

}
