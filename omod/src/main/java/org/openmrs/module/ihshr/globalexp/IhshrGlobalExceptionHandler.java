package org.openmrs.module.ihshr.globalexp;

import java.time.LocalDateTime;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.module.ihshr.exp.InvalidParamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice("ihshrGlobalExceptionHandler")
public class IhshrGlobalExceptionHandler {
	
	@ExceptionHandler({ InvalidParamException.class })
	public ResponseEntity<?> badRequest(InvalidParamException ex, HttpServletRequest request) {
		ex.printStackTrace();
		ErrorResponse response = new ErrorResponse();
		response.setTimestamp(LocalDateTime.now());
		response.setStatus(HttpStatus.BAD_REQUEST.value());
		response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
		response.setPath(request.getRequestURI());
		response.setMessage(ex.getMessage());
		return new ResponseEntity(response, HttpStatus.BAD_REQUEST);
	}
	
}
