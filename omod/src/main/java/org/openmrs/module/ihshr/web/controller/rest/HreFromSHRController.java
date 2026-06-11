package org.openmrs.module.ihshr.web.controller.rest;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Map;

import org.json.JSONException;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.service.HREBundleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/health-record-exchange/api/v1/shr/bundle")
public class HreFromSHRController {
	
	@RequestMapping(value = "/{resourceType}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getResource(@PathVariable String resourceType, @RequestParam Map<String, String> reqParam)
	        throws UnsupportedEncodingException, ParseException, JSONException {
		HREBundleService hreBundleService = Context.getRegisteredComponent("ihshrHreBundleService", HREBundleService.class);
		return new ResponseEntity<>(hreBundleService.getBundle(resourceType, reqParam), HttpStatus.OK);
	}
}
