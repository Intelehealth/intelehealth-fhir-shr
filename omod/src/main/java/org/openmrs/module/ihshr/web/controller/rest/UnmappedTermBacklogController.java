package org.openmrs.module.ihshr.web.controller.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.backlog.IhshrUnmappedTermService;
import org.openmrs.module.ihshr.backlog.UnmappedTermAggregate;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * §8.6 backlog reporting API for terminologists curating lookup JSON files.
 */
@Controller
@RequestMapping("/health-record-exchange/api/v1/backlog")
public class UnmappedTermBacklogController {
	
	@RequestMapping(value = "/top", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> topUnmapped(@RequestParam(value = "artifact", required = false) String artifact,
	        @RequestParam(value = "limit", defaultValue = "50") int limit,
	        @RequestParam(value = "unresolvedOnly", defaultValue = "true") boolean unresolvedOnly) {
		IhshrUnmappedTermService service = getService();
		UnmappedTermArtifact resolvedArtifact = StringUtils.isBlank(artifact) ? null : UnmappedTermArtifact
		        .fromCode(artifact);
		List<UnmappedTermAggregate> rows = service.getTopUnmapped(resolvedArtifact, limit, unresolvedOnly);
		List<Map<String, Object>> payload = new ArrayList<Map<String, Object>>();
		for (UnmappedTermAggregate row : rows) {
			Map<String, Object> item = new HashMap<String, Object>();
			item.put("artifact", row.getArtifact());
			item.put("termText", row.getTermText());
			item.put("lookupFile", row.getLookupFile());
			item.put("lookupType", row.getLookupType());
			item.put("totalOccurrences", row.getTotalOccurrences());
			item.put("distinctObsCount", row.getDistinctObsCount());
			item.put("firstSeen", row.getFirstSeen());
			item.put("lastSeen", row.getLastSeen());
			payload.add(item);
		}
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("artifact", artifact);
		body.put("limit", limit);
		body.put("count", payload.size());
		body.put("rows", payload);
		return new ResponseEntity<Object>(body, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/export.csv", method = RequestMethod.GET, produces = "text/csv")
	public ResponseEntity<String> exportCsv(@RequestParam(value = "artifact", required = false) String artifact,
	        @RequestParam(value = "limit", defaultValue = "50") int limit) {
		IhshrUnmappedTermService service = getService();
		UnmappedTermArtifact resolvedArtifact = StringUtils.isBlank(artifact) ? null : UnmappedTermArtifact
		        .fromCode(artifact);
		String csv = service.exportTopUnmappedCsv(resolvedArtifact, limit);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ihshr-unmapped-terms.csv");
		return new ResponseEntity<String>(csv, headers, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/resolve", method = RequestMethod.POST)
	public ResponseEntity<?> resolve(@RequestParam("artifact") String artifact, @RequestParam("termText") String termText) {
		UnmappedTermArtifact resolvedArtifact = UnmappedTermArtifact.fromCode(artifact);
		if (resolvedArtifact == UnmappedTermArtifact.UNKNOWN || StringUtils.isBlank(termText)) {
			return new ResponseEntity<Object>("artifact and termText are required", HttpStatus.BAD_REQUEST);
		}
		int updated = getService().markResolved(resolvedArtifact, termText);
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("artifact", resolvedArtifact.getCode());
		body.put("termText", termText);
		body.put("rowsUpdated", updated);
		return new ResponseEntity<Object>(body, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/report", method = RequestMethod.POST)
	public ResponseEntity<?> runReport() {
		getService().logWeeklyTopUnmappedReport();
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "logged");
		body.put("message", "Top unmapped terms written to server log (see openmrs.log)");
		return new ResponseEntity<Object>(body, HttpStatus.OK);
	}
	
	private static IhshrUnmappedTermService getService() {
		return Context.getRegisteredComponent("ihshrUnmappedTermService", IhshrUnmappedTermService.class);
	}
	
}
