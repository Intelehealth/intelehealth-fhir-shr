package org.openmrs.module.ihshr.service;

import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.api.context.Context;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.utils.ReqParam;
import org.springframework.stereotype.Service;

@Service("ihshrBundleService")
public class BundleService {
	
	public String getBundle(String resourecType, Map<String, String> reqParam) {
		FhirConfig fhirConfig = Context.getRegisteredComponent("ihshrFhirConfig", FhirConfig.class);
		Bundle results = fhirConfig.getOpenCRFhirContext().search()
		        .byUrl(resourecType + "?" + ReqParam.toQueryParam(reqParam)).returnBundle(Bundle.class).execute();
		
		String response = fhirConfig.newJsonParser().setPrettyPrint(true).encodeResourceToString(results);
		
		System.err.println("DDD>>>>>>>>" + response);
		
		return response;
	}
}
