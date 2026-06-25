package org.openmrs.module.ihshr.capability;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.pull.ShrPullRecordType;
import org.openmrs.module.ihshr.pull.ShrPullView;
import org.openmrs.module.ihshr.utils.ShrFhirUrlSupport;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serves the ihshr SHR integration requirements CapabilityStatement (doc companion to
 * {@code docs/IH-SHR-Conformance-CapabilityStatement.json}).
 */
@Service("ihshrShrCapabilityStatementService")
public class ShrCapabilityStatementService {
	
	private static final String TEMPLATE_RESOURCE = "IH-SHR-Conformance-CapabilityStatement.json";
	
	private static final String MODULE_VERSION = "1.0.0-SNAPSHOT";
	
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	
	@SuppressWarnings("unchecked")
	public Map<String, Object> getIntegrationRequirements() {
		Map<String, Object> document = loadTemplate();
		patchDeployment(document);
		return document;
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, Object> loadTemplate() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) {
			loader = ShrCapabilityStatementService.class.getClassLoader();
		}
		try (InputStream in = loader.getResourceAsStream(TEMPLATE_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource: " + TEMPLATE_RESOURCE);
			}
			return JSON_MAPPER.readValue(in, LinkedHashMap.class);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to load " + TEMPLATE_RESOURCE, ex);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void patchDeployment(Map<String, Object> document) {
		document.put("date", formatTodayUtc());
		
		Map<String, Object> software = (Map<String, Object>) document.get("software");
		if (software != null) {
			software.put("version", MODULE_VERSION);
		}
		
		String shrUrl = StringUtils.removeEnd(ShrFhirUrlSupport.resolveShrFhirBaseUrl(), "/");
		Map<String, Object> implementation = (Map<String, Object>) document.get("implementation");
		if (implementation != null && StringUtils.isNotBlank(shrUrl)) {
			implementation.put("url", shrUrl);
			implementation.put("description", "Configured SHR deployment (ihshr.opencr.shr.url)");
		}
		
		document.put("ihshrPullViews", buildPullViews());
		document.put("ihshrRecordTypes", buildRecordTypes());
	}
	
	private static String formatTodayUtc() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date());
	}
	
	private static List<String> buildPullViews() {
		List<String> views = new ArrayList<String>();
		for (ShrPullView view : ShrPullView.values()) {
			views.add(view.name().toLowerCase().replace('_', '-'));
		}
		return views;
	}
	
	private static List<String> buildRecordTypes() {
		List<String> types = new ArrayList<String>();
		for (ShrPullRecordType type : ShrPullRecordType.values()) {
			types.add(type.getFhirType());
		}
		return types;
	}
}
