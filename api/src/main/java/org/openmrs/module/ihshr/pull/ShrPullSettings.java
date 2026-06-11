package org.openmrs.module.ihshr.pull;

import org.openmrs.module.ihshr.utils.IhshrPropertyResolver;

public final class ShrPullSettings {
	
	private static final String KEY_DEFAULT_COUNT = "shr.pull.default.count";
	
	private static final String KEY_MAX_COUNT = "shr.pull.max.count";
	
	private static final String KEY_DEFAULT_MONTHS = "shr.pull.default.months";
	
	private ShrPullSettings() {
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
