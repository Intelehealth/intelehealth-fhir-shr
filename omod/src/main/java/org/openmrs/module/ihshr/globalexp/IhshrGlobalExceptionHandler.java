package org.openmrs.module.ihshr.globalexp;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.module.ihshr.exp.InvalidParamException;
import org.openmrs.module.ihshr.pull.ShrPullErrorCode;
import org.openmrs.module.ihshr.pull.ShrPullException;
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
	
	@ExceptionHandler({ ShrPullException.class })
	public ResponseEntity<?> shrPullException(ShrPullException ex, HttpServletRequest request) {
		return pullErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
	}
	
	@ExceptionHandler({ APIAuthenticationException.class })
	public ResponseEntity<?> notAuthenticated(APIAuthenticationException ex, HttpServletRequest request) {
		return pullErrorResponse(ShrPullErrorCode.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
	}
	
	@ExceptionHandler({ APIException.class })
	public ResponseEntity<?> apiException(APIException ex, HttpServletRequest request) {
		String message = ex.getMessage() != null ? ex.getMessage() : "OpenMRS API error";
		ShrPullErrorCode code = message.contains("Privileges required") ? ShrPullErrorCode.FORBIDDEN
		        : ShrPullErrorCode.INVALID_FILTER;
		return pullErrorResponse(code, message, request.getRequestURI());
	}
	
	private static ResponseEntity<?> pullErrorResponse(ShrPullErrorCode code, String message, String path) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("error", code.name());
		body.put("message", message);
		body.put("path", path);
		HttpStatus status = mapStatus(code);
		return new ResponseEntity<Object>(body, status);
	}
	
	private static HttpStatus mapStatus(ShrPullErrorCode code) {
		switch (code) {
			case PATIENT_NOT_FOUND:
				return HttpStatus.NOT_FOUND;
			case PATIENT_NOT_SYNCED:
			case SHR_AMBIGUOUS_PATIENT:
				return HttpStatus.CONFLICT;
			case SHR_UPSTREAM_ERROR:
				return HttpStatus.BAD_GATEWAY;
			case UNAUTHORIZED_PAGE_URL:
			case INVALID_FILTER:
				return HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED:
				return HttpStatus.UNAUTHORIZED;
			case FORBIDDEN:
				return HttpStatus.FORBIDDEN;
			default:
				return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}
	
}
