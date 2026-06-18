package org.openmrs.module.ihshr.web.controller.rest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.pull.ShrBinaryContent;
import org.openmrs.module.ihshr.pull.ShrHistoryRequest;
import org.openmrs.module.ihshr.pull.ShrPullAuthSupport;
import org.openmrs.module.ihshr.pull.ShrPullErrorCode;
import org.openmrs.module.ihshr.pull.ShrPullException;
import org.openmrs.module.ihshr.pull.ShrPullResult;
import org.openmrs.module.ihshr.pull.ShrPullResponseSupport;
import org.openmrs.module.ihshr.pull.ShrPullService;
import org.springframework.http.CacheControl;
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
 * <p>
 * <b>Base URL:</b> {@code openmrsBase}/ws/rest/v1/ihshr/shr/... (same pattern as ihmodule
 * {@code ConfigFacilityRestController}). Legacy servlet paths {@code module/ihshr/*.form} are also
 * mapped (same pattern as {@code PatientExchangeProxyRestController}).
 */
@Controller
@RequestMapping("/rest/v1/ihshr/shr")
public class ShrPullController {
	
	@RequestMapping(value = "/patients/{openmrsPatientUuid}/resolve", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> resolvePatient(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			Object body = getService().resolvePatient(openmrsPatientUuid);
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}", method = RequestMethod.GET)
	public ResponseEntity<?> getHistory(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(queryParams);
			ShrPullResult result = getService().getHistory(openmrsPatientUuid, request);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}/page", method = RequestMethod.GET)
	public ResponseEntity<?> getPage(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			String pageUrl = queryParams.get("url");
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			ShrPullResult result = getService().getPage(openmrsPatientUuid, pageUrl, format, includeLocalEcho);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/history/{openmrsPatientUuid}/refresh", method = RequestMethod.GET)
	public ResponseEntity<?> refresh(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			String since = queryParams.get("since");
			int count = parseCount(queryParams.get("count"), 50);
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			ShrPullResult result = getService().refresh(openmrsPatientUuid, since, count, includeLocalEcho, format);
			return formatResponse(result);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/search/{openmrsPatientUuid}/{resourceType}", method = RequestMethod.GET)
	public ResponseEntity<?> search(@PathVariable("openmrsPatientUuid") String openmrsPatientUuid,
	        @PathVariable("resourceType") String resourceType, @RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
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
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/binary/{binaryId}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> streamBinary(@PathVariable("binaryId") String binaryId) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			ShrBinaryContent content = getService().readBinaryBytes(binaryId);
			MediaType mediaType = MediaType.parseMediaType(StringUtils.defaultIfBlank(content.getContentType(),
			    MediaType.APPLICATION_OCTET_STREAM_VALUE));
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(mediaType).body(content.getData());
		}
		catch (ShrPullException ex) {
			return new ResponseEntity<byte[]>(mapStatus(ex.getErrorCode()));
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = "/content/Binary/{binaryId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> readBinaryJson(@PathVariable("binaryId") String binaryId) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			Object body = getService().readBinaryContent(binaryId);
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
		}
		catch (ShrPullException ex) {
			return errorResponse(ex);
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	private static int parseCount(String value, int defaultValue) {
		if (StringUtils.isBlank(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Invalid count: " + value);
		}
	}
	
	private ResponseEntity<?> formatResponse(ShrPullResult result) {
		return ShrPullResponseSupport.toResponse(result, getService());
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
			case UNAUTHORIZED:
				return HttpStatus.UNAUTHORIZED;
			case FORBIDDEN:
				return HttpStatus.FORBIDDEN;
			default:
				return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}
	
	private ShrPullService getService() {
		return Context.getRegisteredComponent("ihshrShrPullService", ShrPullService.class);
	}
}
