package org.openmrs.module.ihshr.config;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.openmrs.module.ihshr.utils.HttpWebClient;
import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads {@code fhir_module.shr} from the published config API to decide whether outbound SHR visit
 * push is enabled (IHSHR module only; ihmodule patient FHIR sync uses {@code fhir_module.fhir}).
 */
@Service("publishedConfigShrSyncGateService")
public class PublishedConfigShrSyncGateService {
	
	private static final Logger log = LoggerFactory.getLogger(PublishedConfigShrSyncGateService.class);
	
	public static final String PROP_PUBLISHED_CONFIG_URL = "intelehealth.config.published.url";
	
	public static final String PROP_CACHE_SECONDS = "intelehealth.fhir.sync.published.config.cache.seconds";
	
	private static final int DEFAULT_CACHE_SECONDS = 60;
	
	private volatile CachedDecision cachedDecision;
	
	public boolean isShrSyncEnabled() {
		return resolveDecision().enabled;
	}
	
	void clearCacheForTests() {
		cachedDecision = null;
	}
	
	private ResolvedDecision resolveDecision() {
		long now = System.currentTimeMillis();
		CachedDecision local = cachedDecision;
		if (local != null && now < local.expiresAtMs) {
			return local.decision;
		}
		ResolvedDecision fresh = fetchFromApi();
		cachedDecision = new CachedDecision(fresh, now + cacheTtlMs());
		return fresh;
	}
	
	private ResolvedDecision fetchFromApi() {
		String url = resolvePublishedConfigUrl();
		if (StringUtils.isBlank(url)) {
			log.error("Published config URL is blank ({}); treating SHR sync as enabled", PROP_PUBLISHED_CONFIG_URL);
			return enabledDefault("missing-url");
		}
		try {
			String body = HttpWebClient.getJson(url);
			if (StringUtils.isBlank(body)) {
				log.error("Published config API returned empty body; treating SHR sync as enabled");
				return enabledDefault("empty-body");
			}
			JSONObject root = new JSONObject(body);
			JSONObject fhirModule = root.optJSONObject("fhir_module");
			if (fhirModule == null) {
				log.warn("Published config JSON has no fhir_module object; treating SHR sync as disabled");
				return new ResolvedDecision(false, "missing-fhir_module");
			}
			boolean shr = fhirModule.optBoolean("shr", false);
			log.debug("Published config fhir_module.shr={} => SHR sync {}", shr, shr ? "enabled" : "disabled");
			return new ResolvedDecision(shr, "fhir_module.shr=" + shr);
		}
		catch (RuntimeException ex) {
			log.warn("Published config API call failed ({}); treating SHR sync as enabled: {}", url, ex.getMessage());
			return enabledDefault("api-error");
		}
	}
	
	private static ResolvedDecision enabledDefault(String reason) {
		return new ResolvedDecision(true, reason);
	}
	
	private long cacheTtlMs() {
		String raw = IhshrPropertyResolver.resolve("fhir.sync.published.config.cache.seconds", PROP_CACHE_SECONDS);
		int seconds = DEFAULT_CACHE_SECONDS;
		if (StringUtils.isNotBlank(raw)) {
			try {
				seconds = Integer.parseInt(raw.trim());
			}
			catch (NumberFormatException ex) {
				log.warn("Invalid {}='{}'; using {}s", PROP_CACHE_SECONDS, raw, DEFAULT_CACHE_SECONDS);
			}
		}
		if (seconds < 0) {
			seconds = 0;
		}
		return seconds * 1000L;
	}
	
	private String resolvePublishedConfigUrl() {
		return IhshrPropertyResolver.resolve("config.published.url", PROP_PUBLISHED_CONFIG_URL);
	}
	
	private static final class ResolvedDecision {
		
		final boolean enabled;
		
		final String reason;
		
		ResolvedDecision(boolean enabled, String reason) {
			this.enabled = enabled;
			this.reason = reason;
		}
	}
	
	private static final class CachedDecision {
		
		final ResolvedDecision decision;
		
		final long expiresAtMs;
		
		CachedDecision(ResolvedDecision decision, long expiresAtMs) {
			this.decision = decision;
			this.expiresAtMs = expiresAtMs;
		}
	}
}
