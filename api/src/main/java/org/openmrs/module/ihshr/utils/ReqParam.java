package org.openmrs.module.ihshr.utils;

import java.util.Map;

public class ReqParam {
	
	public static String toQueryParam(Map<String, String> reqParam) {
		if (reqParam == null || reqParam.size() == 0)
			return "";
		StringBuilder queryParam = new StringBuilder();
		for (Map.Entry<String, String> entry : reqParam.entrySet()) {
			queryParam.append("&" + entry.getKey() + "=" + entry.getValue());
		}
		String theSearchParamString = queryParam.substring(1);
		return theSearchParamString;
	}
	
}
