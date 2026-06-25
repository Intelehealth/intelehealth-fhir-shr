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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Legacy {@code module/ihshr/*.form} servlet paths for SHR pull. REST clients should prefer
 * {@link ShrPullController} at {@code /ws/rest/v1/ihshr/shr/...}.
 */
@Controller
public class ShrPullFormController {
	
	@RequestMapping(value = "module/ihshr/shrPatientResolve.form", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> resolvePatient(@RequestParam("openmrsPatientUuid") String openmrsPatientUuid) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			Object body = getService().resolvePatient(openmrsPatientUuid.trim());
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
	
	@RequestMapping(value = "module/ihshr/shrHistory.form", method = RequestMethod.GET)
	public ResponseEntity<?> getHistory(@RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(queryParams);
			ShrPullResult result = getService().getHistory(requirePatientUuid(queryParams.get("openmrsPatientUuid")),
			    request);
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
	
	@RequestMapping(value = "module/ihshr/shrHistoryPage.form", method = RequestMethod.GET)
	public ResponseEntity<?> getPage(@RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			String pageUrl = queryParams.get("url");
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			ShrPullResult result = getService().getPage(requirePatientUuid(queryParams.get("openmrsPatientUuid")), pageUrl,
			    format, includeLocalEcho);
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
	
	@RequestMapping(value = "module/ihshr/shrHistoryRefresh.form", method = RequestMethod.GET)
	public ResponseEntity<?> refresh(@RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			String since = queryParams.get("since");
			int count = parseCount(queryParams.get("count"), 50);
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			ShrPullResult result = getService().refresh(requirePatientUuid(queryParams.get("openmrsPatientUuid")), since,
			    count, includeLocalEcho, format);
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
	
	@RequestMapping(value = "module/ihshr/shrSearch.form", method = RequestMethod.GET)
	public ResponseEntity<?> search(@RequestParam Map<String, String> queryParams) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			String resourceType = requireResourceType(queryParams.get("resourceType"));
			String format = StringUtils.defaultIfBlank(queryParams.get("format"), "envelope");
			boolean includeLocalEcho = Boolean.parseBoolean(StringUtils.defaultString(queryParams.get("includeLocalEcho")));
			Map<String, String> extra = new HashMap<String, String>(queryParams);
			extra.remove("format");
			extra.remove("includeLocalEcho");
			extra.remove("openmrsPatientUuid");
			extra.remove("resourceType");
			ShrPullResult result = getService().searchResource(requirePatientUuid(queryParams.get("openmrsPatientUuid")),
			    resourceType, extra, includeLocalEcho, format);
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
	
	@RequestMapping(value = "module/ihshr/shrBinaryStream.form", method = RequestMethod.GET)
	public ResponseEntity<byte[]> streamBinary(@RequestParam("binaryId") String binaryId) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			ShrBinaryContent content = getService().readBinaryBytes(binaryId.trim());
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
	
	@RequestMapping(value = "module/ihshr/shrBinaryJson.form", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> readBinaryJson(@RequestParam("binaryId") String binaryId) {
		try {
			ShrPullAuthSupport.requirePullAccess();
			Object body = getService().readBinaryContent(binaryId.trim());
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
	
	private static String requirePatientUuid(String uuid) {
		if (StringUtils.isBlank(uuid)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "openmrsPatientUuid is required");
		}
		return uuid.trim();
	}
	
	private static String requireResourceType(String resourceType) {
		if (StringUtils.isBlank(resourceType)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "resourceType is required");
		}
		return resourceType.trim();
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
