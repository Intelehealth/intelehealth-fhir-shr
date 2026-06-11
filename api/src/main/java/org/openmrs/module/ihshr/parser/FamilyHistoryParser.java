package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;
import org.openmrs.module.ihshr.utils.FamilyHistoryRoleCodes;

/**
 * Parses family history clinical HTML: condition-first segments with comma-separated relatives.
 */
public class FamilyHistoryParser {
	
	private static final Pattern HTML_BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BULLET = Pattern.compile("^•\\s*");
	
	public List<ParsedFamilyHistoryRelative> parse(String raw) {
		String text = ClinicalJsonValueTexts.normalizeHtml(ClinicalJsonValueTexts.extractClinicalHtml(raw));
		text = HTML_BR.matcher(text).replaceAll(" ");
		text = BULLET.matcher(text.trim()).replaceFirst("");
		int colon = text.indexOf(':');
		if (colon >= 0) {
			text = text.substring(colon + 1);
		}
		
		List<Entry> entries = new ArrayList<Entry>();
		for (String segment : text.split("\\.+")) {
			String s = BULLET.matcher(segment.trim()).replaceFirst("");
			if (s.isEmpty()) {
				continue;
			}
			List<String> parts = Arrays.stream(s.split(",")).map(String::trim).filter(p -> !p.isEmpty())
			        .collect(Collectors.toList());
			if (parts.size() < 2) {
				continue;
			}
			String condition = parts.get(0);
			if (isNoneCondition(condition)) {
				continue;
			}
			entries.add(new Entry(condition, parts.subList(1, parts.size())));
		}
		
		Map<String, ParsedFamilyHistoryRelative> byRelative = new LinkedHashMap<String, ParsedFamilyHistoryRelative>();
		for (Entry entry : entries) {
			for (String relative : entry.relatives) {
				String roleCode = FamilyHistoryRoleCodes.lookupRoleCode(relative);
				String key = roleCode != null ? roleCode : relative.toLowerCase(Locale.ROOT).trim();
				ParsedFamilyHistoryRelative parsed = byRelative.get(key);
				if (parsed == null) {
					parsed = new ParsedFamilyHistoryRelative(relative, roleCode, key);
					byRelative.put(key, parsed);
				}
				parsed.addCondition(entry.condition);
			}
		}
		return new ArrayList<ParsedFamilyHistoryRelative>(byRelative.values());
	}
	
	private static boolean isNoneCondition(String condition) {
		return "none".equalsIgnoreCase(condition.trim().replace(".", ""));
	}
	
	private static final class Entry {
		
		final String condition;
		
		final List<String> relatives;
		
		Entry(String condition, List<String> relatives) {
			this.condition = condition;
			this.relatives = relatives;
		}
	}
	
}
