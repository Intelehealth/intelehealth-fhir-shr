package org.openmrs.module.ihshr.synclog;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryResponseComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds {@code resource_map} JSON from a transaction-response Bundle (doc §3.1 step 15).
 */
public final class ShrSyncResourceMapBuilder {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private ShrSyncResourceMapBuilder() {
	}
	
	public static String buildFromResponseBundle(Bundle responseBundle, Bundle requestBundle) {
		if (responseBundle == null || !responseBundle.hasEntry()) {
			return "{}";
		}
		ObjectNode root = MAPPER.createObjectNode();
		for (int i = 0; i < responseBundle.getEntry().size(); i++) {
			BundleEntryComponent responseEntry = responseBundle.getEntry().get(i);
			BundleEntryResponseComponent response = responseEntry.getResponse();
			if (response == null || !response.hasLocation()) {
				continue;
			}
			String openmrsKey = resolveOpenmrsKey(requestBundle, i, responseEntry);
			if (StringUtils.isBlank(openmrsKey)) {
				continue;
			}
			String location = response.getLocation();
			String shrId = parseResourceIdFromLocation(location);
			String version = parseVersionFromLocation(location);
			ObjectNode entry = MAPPER.createObjectNode();
			if (shrId != null) {
				entry.put("shr_id", shrId);
			}
			if (version != null) {
				entry.put("version", version);
			}
			root.set(openmrsKey, entry);
		}
		return root.toString();
	}
	
	private static String resolveOpenmrsKey(Bundle requestBundle, int index, BundleEntryComponent responseEntry) {
		if (requestBundle != null && requestBundle.hasEntry() && index < requestBundle.getEntry().size()) {
			BundleEntryComponent requestEntry = requestBundle.getEntry().get(index);
			if (requestEntry.hasRequest() && requestEntry.getRequest().hasUrl()) {
				String url = requestEntry.getRequest().getUrl();
				int slash = url.lastIndexOf('/');
				if (slash >= 0 && slash < url.length() - 1) {
					return url.substring(slash + 1);
				}
			}
			if (requestEntry.hasResource() && requestEntry.getResource().hasIdElement()
			        && requestEntry.getResource().getIdElement().hasIdPart()) {
				return requestEntry.getResource().getIdElement().getIdPart();
			}
		}
		if (responseEntry.hasResponse() && responseEntry.getResponse().hasLocation()) {
			return parseResourceIdFromLocation(responseEntry.getResponse().getLocation());
		}
		return null;
	}
	
	private static String parseResourceIdFromLocation(String location) {
		if (StringUtils.isBlank(location)) {
			return null;
		}
		String trimmed = location.trim();
		int history = trimmed.indexOf("/_history");
		if (history > 0) {
			trimmed = trimmed.substring(0, history);
		}
		int slash = trimmed.lastIndexOf('/');
		if (slash >= 0 && slash < trimmed.length() - 1) {
			return trimmed.substring(slash + 1);
		}
		return trimmed;
	}
	
	private static String parseVersionFromLocation(String location) {
		if (StringUtils.isBlank(location)) {
			return null;
		}
		int history = location.indexOf("/_history/");
		if (history < 0) {
			return null;
		}
		return location.substring(history + "/_history/".length()).trim();
	}
}
