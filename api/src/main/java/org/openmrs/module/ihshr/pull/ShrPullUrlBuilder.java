package org.openmrs.module.ihshr.pull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds encoded FHIR search URLs (doc §4 / §9).
 */
public final class ShrPullUrlBuilder {
	
	private final Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
	
	public ShrPullUrlBuilder param(String key, String value) {
		if (key == null || value == null || value.trim().isEmpty()) {
			return this;
		}
		List<String> values = params.get(key);
		if (values == null) {
			values = new ArrayList<String>();
			params.put(key, values);
		}
		values.add(value.trim());
		return this;
	}
	
	public ShrPullUrlBuilder paramIfPresent(String key, String value) {
		if (value != null && !value.trim().isEmpty()) {
			param(key, value);
		}
		return this;
	}
	
	public String build(String resourcePath) {
		StringBuilder sb = new StringBuilder(resourcePath);
		boolean first = true;
		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			for (String value : entry.getValue()) {
				sb.append(first ? '?' : '&');
				first = false;
				sb.append(encode(entry.getKey())).append('=').append(encode(value));
			}
		}
		return sb.toString();
	}
	
	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException("UTF-8 not supported", ex);
		}
	}
}
