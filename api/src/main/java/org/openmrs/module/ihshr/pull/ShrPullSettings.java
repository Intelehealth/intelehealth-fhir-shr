package org.openmrs.module.ihshr.pull;

import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

public final class ShrPullSettings {
	
	private static final String KEY_DEFAULT_COUNT = "shr.pull.default.count";
	
	private static final String KEY_MAX_COUNT = "shr.pull.max.count";
	
	private static final String KEY_DEFAULT_MONTHS = "shr.pull.default.months";
	
	private static final String KEY_MAX_PARALLEL = "shr.pull.max.parallel";
	
	private static final String KEY_SOURCE_FILTER_ENABLED = "shr.pull.source.filter.enabled";
	
	private ShrPullSettings() {
	}
	
	public static int maxParallelQueries() {
		return parseInt(IhshrPropertyResolver.resolve(KEY_MAX_PARALLEL), 5);
	}
	
	public static int defaultCount() {
		return parseInt(IhshrPropertyResolver.resolve(KEY_DEFAULT_COUNT), 50);
	}
	
	public static int maxCount() {
		return parseInt(IhshrPropertyResolver.resolve(KEY_MAX_COUNT), 200);
	}
	
	public static int defaultMonths() {
		return parseInt(IhshrPropertyResolver.resolve(KEY_DEFAULT_MONTHS), 12);
	}
	
	/**
	 * When true, pull queries send {@code _source:not} / {@code _source} (doc §5.3 no-echo). Many
	 * HAPI servers disable the {@code _source} search parameter; leave false unless SHR enables it.
	 */
	public static boolean sourceFilterEnabled() {
		String value = IhshrPropertyResolver.resolve(KEY_SOURCE_FILTER_ENABLED);
		if (value == null || value.trim().isEmpty()) {
			return false;
		}
		return Boolean.parseBoolean(value.trim());
	}
	
	private static int parseInt(String value, int fallback) {
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
