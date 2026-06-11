package org.openmrs.module.ihshr.utils;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;

/**
 * Resolves IHSHR configuration: OpenMRS global property {@code ihshr.<key>} first, then
 * {@code ihshr.properties} on the classpath. Values can be changed at runtime in Administration →
 * Manage Global Properties.
 */
public final class IhshrPropertyResolver {
	
	public static final String GLOBAL_PROPERTY_PREFIX = "ihshr.";
	
	private static final Properties CLASSPATH_DEFAULTS = ModuleClasspathPropertiesLoader
	        .loadMergedInOrder("ihshr.properties");
	
	private IhshrPropertyResolver() {
	}
	
	public static String resolve(String propertyKey) {
		return resolve(propertyKey, null);
	}
	
	/**
	 * @param propertyKey key as in {@code ihshr.properties} (e.g. {@code local.openmrs.url})
	 * @param legacyGlobalPropertyKey optional unprefixed GP name for backward compatibility
	 */
	public static String resolve(String propertyKey, String legacyGlobalPropertyKey) {
		String value = resolveFromGlobalProperties(propertyKey, legacyGlobalPropertyKey);
		if (StringUtils.isBlank(value) && CLASSPATH_DEFAULTS != null) {
			value = CLASSPATH_DEFAULTS.getProperty(propertyKey);
		}
		return StringUtils.trimToEmpty(value);
	}
	
	private static String resolveFromGlobalProperties(String propertyKey, String legacyGlobalPropertyKey) {
		try {
			if (!Context.isSessionOpen()) {
				return null;
			}
			String prefixed = GLOBAL_PROPERTY_PREFIX + propertyKey;
			String value = Context.getAdministrationService().getGlobalProperty(prefixed);
			if (StringUtils.isBlank(value) && StringUtils.isNotBlank(legacyGlobalPropertyKey)) {
				value = Context.getAdministrationService().getGlobalProperty(legacyGlobalPropertyKey);
			}
			if (StringUtils.isBlank(value)) {
				value = Context.getAdministrationService().getGlobalProperty(propertyKey);
			}
			return value;
		}
		catch (Exception ignored) {
			return null;
		}
	}
}
