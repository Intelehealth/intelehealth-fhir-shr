package org.openmrs.module.ihshr.web.controller.rest;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.datatype.ConfigFacilityDataType;
import org.openmrs.module.ihshr.service.ConfigDataSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/health-record-exchange/api/v1/control")
public class ActivityController {
	
	@RequestMapping(value = "/activity", method = RequestMethod.GET)
	public ResponseEntity<?> activity() throws ParseException, JSONException, IOException {
		ConfigDataSyncService configDataSyncService = Context.getRegisteredComponent("ihshrConfigDataSyncService",
		    ConfigDataSyncService.class);
		HashMap<String, Object> object = new HashMap<>();
		object.put("status", HttpStatus.OK);
		object.put("message", "Health Record Exchange is alive");
		object.put("responseTime", new Date());
		object.put("configDataSyncStatus",
		    configDataSyncService.getConfigDataSync(ConfigFacilityDataType.HEALTH_RECORD));
		return new ResponseEntity<>(object, HttpStatus.OK);
	}
}
