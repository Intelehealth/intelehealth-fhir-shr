package org.openmrs.module.ihshr.web.controller.rest;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

import org.json.JSONException;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.service.DiagnosticReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/health-record-exchange/api/v1/diagnostic")
public class DiagnosticController {
	
	@RequestMapping(value = "/get-report", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> activity(@RequestParam Map<String, String> reqParam)
	        throws ParseException, JSONException, IOException {
		DiagnosticReportService diagService = Context.getRegisteredComponent("ihshrDiagnosticReportService",
		    DiagnosticReportService.class);
		return new ResponseEntity<>(diagService.getReport(reqParam), HttpStatus.OK);
	}
}
