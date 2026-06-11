package org.openmrs.module.ihshr.parser;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public final class MedicalHistoryNegativeFilter {
	
	private MedicalHistoryNegativeFilter() {
	}
	
	public static boolean isNegative(String value, List<String> negativePatterns) {
		if (StringUtils.isBlank(value)) {
			return true;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (String pattern : negativePatterns) {
			if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(normalized).find()) {
				return true;
			}
		}
		return false;
	}
	
}
