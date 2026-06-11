package org.openmrs.module.ihshr.web.controller.rest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullErrorCode;
import org.openmrs.module.ihshr.pull.ShrPullException;
import org.openmrs.module.ihshr.pull.ShrPullResult;
import org.openmrs.module.ihshr.pull.ShrPullService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Doctor portal SHR pull API (doc §4 / §9).
 */
@Controller
@RequestMapping("/health-record-exchange/api/v1/shr")
public class ShrPullController {
	
	@RequestMapping(value = "/patients/{openmrsPatientUuid}/resolve", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> resolvePatient(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid) {
		try {
			return ResponseEntity.ok(getService().resolvePatient(openmrsPatientUuid));
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}", method = RequestMethod.GET)
	public ResponseEntity<?> getHistory(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam Map<String, String> queryParams) {
		try {
			ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(queryParams);
			ShrPullResult result = getService().getHistory(openmrsPatientUuid, request);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}/page", method = RequestMethod.GET)
	public ResponseEntity<?> getPage(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam("url") String pageUrl, @RequestParam(value = "format", defaultValue = "envelope") String format) {
		try {
			ShrPullResult result = getService().getPage(openmrsPatientUuid, pageUrl, format);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}/refresh", method = RequestMethod.GET)
	public ResponseEntity<?> refresh(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam("since") String since, @RequestParam(value = "count", defaultValue = "50") int count,
	        @RequestParam(value = "includeLocalEcho", defaultValue = "false") boolean includeLocalEcho,
	        @RequestParam(value = "format", defaultValue = "envelope") String format) {
		try {
			ShrPullResult result = getService().refresh(openmrsPatientUuid, since, count, includeLocalEcho, format);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	@RequestMapping(value = "/search/{openmrsPatientUuid}/{resourceType}", method = RequestMethod.GET)
	public ResponseEntity<?> search(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @PathVariable("resourceType") String resourceType, @RequestParam Map<String, String> queryParams) {
		try {
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			Map<String, String> extra = new HashMap<String, String>(queryParams);
			extra.remove("format");
			extra.remove("includeLocalEcho");
			ShrPullResult result = getService().searchResource(openmrsPatientUuid, resourceType, extra, includeLocalEcho,
			    format);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	@RequestMapping(value = "/content/Binary/{binaryId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> readBinary(@PathVariable("binaryId") String binaryId) {
		try {
			return ResponseEntity.ok(getService().readBinaryContent(binaryId));
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
	}
	
	private ResponseEntity<?> formatResponse(ShrPullResult result) {
		String format = result.getRequest() != null ? result.getRequest().getFormat() : "envelope";
		if ("fhir".equalsIgnoreCase(format) || "fhir-merged".equalsIgnoreCase(format)) {
			String body = getService().encodeBundle(result.getMerged());
			return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/fhir+json")).body(body);
		}
		return ResponseEntity.ok(getService().toEnvelope(result));
	}
	
	private ResponseEntity<?> errorResponse(ShrPullException ex) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("error", ex.getErrorCode().name());
		body.put("message", ex.getMessage());
		return new ResponseEntity<Object>(body, mapStatus(ex.getErrorCode()));
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
			default:
				return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}
	
	private ShrPullService getService() {
		return Context.getRegisteredComponent("ihshrShrPullService", ShrPullService.class);
	}
}
