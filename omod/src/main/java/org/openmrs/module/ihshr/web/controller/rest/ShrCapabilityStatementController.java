package org.openmrs.module.ihshr.web.controller.rest;

import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.capability.ShrCapabilityStatementService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Publishes the ihshr SHR integration requirements CapabilityStatement (FHIR-shaped JSON).
 * <p>
 * Live SHR server capabilities remain at {@code GET shr-base}/metadata}; this endpoint documents
 * the minimum subset required by ihshr push and pull.
 */
@Controller
public class ShrCapabilityStatementController {
	
	@RequestMapping(value = { "/rest/v1/ihshr/shr/capability-statement",
	        "/health-record-exchange/api/v1/shr/capability-statement", "module/ihshr/shrCapabilityStatement.form" }, method = RequestMethod.GET, produces = {
	        MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
	public ResponseEntity<?> getCapabilityStatement() {
		try {
			ShrCapabilityStatementService service = Context.getRegisteredComponent("ihshrShrCapabilityStatementService",
			    ShrCapabilityStatementService.class);
			return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.getIntegrationRequirements());
		}
		catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<Object>("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
