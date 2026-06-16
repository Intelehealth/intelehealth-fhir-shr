package org.openmrs.module.ihshr.pull;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.ihshr.config.FhirConfig;
import org.openmrs.module.ihshr.utils.ShrFhirUrlSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

@Component("ihshrShrFhirPullClient")
public class ShrFhirPullClient {
	
	@Autowired
	@Qualifier("ihshrFhirConfig")
	private FhirConfig fhirConfig;
	
	public Bundle search(String url) {
		try {
			return fhirConfig.searchBundleByUrl(url);
		}
		catch (BaseServerResponseException ex) {
			throw new ShrPullException(ShrPullErrorCode.SHR_UPSTREAM_ERROR, "SHR search failed (" + ex.getStatusCode()
			        + "): " + ex.getMessage(), ex);
		}
		catch (RuntimeException ex) {
			throw new ShrPullException(ShrPullErrorCode.SHR_UPSTREAM_ERROR, "SHR search failed: " + ex.getMessage(), ex);
		}
	}
	
	public Binary readBinary(String binaryId) {
		try {
			return fhirConfig.readBinary(binaryId);
		}
		catch (BaseServerResponseException ex) {
			throw new ShrPullException(ShrPullErrorCode.SHR_UPSTREAM_ERROR, "SHR Binary read failed (" + ex.getStatusCode()
			        + "): " + ex.getMessage(), ex);
		}
		catch (RuntimeException ex) {
			throw new ShrPullException(ShrPullErrorCode.SHR_UPSTREAM_ERROR, "SHR Binary read failed: " + ex.getMessage(), ex);
		}
	}
	
	public void assertAllowedPageUrl(String pageUrl) {
		if (StringUtils.isBlank(pageUrl)) {
			throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "page url is required");
		}
		String trimmed = pageUrl.trim();
		for (String allowedBase : allowedBaseUrls()) {
			if (trimmed.startsWith(allowedBase)) {
				return;
			}
		}
		throw new ShrPullException(ShrPullErrorCode.UNAUTHORIZED_PAGE_URL,
		        "Pagination URL is not under configured SHR pull base");
	}
	
	private String[] allowedBaseUrls() {
		return new String[] { normalizeBase(ShrFhirUrlSupport.resolveShrFhirBaseUrl()) };
	}
	
	private static String normalizeBase(String base) {
		if (base == null) {
			return "";
		}
		String trimmed = base.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
