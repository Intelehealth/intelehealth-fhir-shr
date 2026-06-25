package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;
import org.openmrs.module.ihshr.utils.FamilyHistoryRoleCodes;

/**
 * Parses family history obs text (concept 163211) into per-relative structures for FHIR
 * {@code FamilyMemberHistory}.
 * <p>
 * The mobile UI stores answers <b>condition-first</b> (e.g.
 * {@code Heart Disease, Brother. Diabetes, Mother.}). FHIR expects one {@code FamilyMemberHistory}
 * per relative with multiple {@code condition} entries, so this parser inverts condition-first
 * segments into relative-first {@link ParsedFamilyHistoryRelative} rows.
 */
public class FamilyHistoryParser {
	
	private static final Pattern HTML_BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BULLET = Pattern.compile("^•\\s*");
	
	public List<ParsedFamilyHistoryRelative> parse(String raw) {
		// Only the "en" clinical string from multilingual JSON is used; strip question prefix and HTML.
		String text = ClinicalJsonValueTexts.normalizeHtml(ClinicalJsonValueTexts.extractClinicalHtml(raw));
		text = HTML_BR.matcher(text).replaceAll(" ");
		text = BULLET.matcher(text.trim()).replaceFirst("");
		int colon = text.indexOf(':');
		if (colon >= 0) {
			// Drop leading prompt, e.g. "Do you have a family history... : "
			text = text.substring(colon + 1);
		}
		
		// Phase 1: split into condition-first entries — "Condition, Relative1, Relative2"
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
		
		// Phase 2: invert to relative-first — one ParsedFamilyHistoryRelative per family member (FHIR shape).
		// LinkedHashMap preserves first-seen relative order in the output list.
		Map<String, ParsedFamilyHistoryRelative> byRelative = new LinkedHashMap<String, ParsedFamilyHistoryRelative>();
		for (Entry entry : entries) {
			for (String relative : entry.relatives) {
				String roleCode = FamilyHistoryRoleCodes.lookupRoleCode(relative);
				// Prefer HL7 role code (BRO, MTH) as merge key so "Brother" and "brother" dedupe;
				// fall back to lowercase label when family-relationships.json has no mapping.
				String key = roleCode != null ? roleCode : relative.toLowerCase(Locale.ROOT).trim();
				ParsedFamilyHistoryRelative parsed = byRelative.get(key);
				if (parsed == null) {
					parsed = new ParsedFamilyHistoryRelative(relative, roleCode, key);
					byRelative.put(key, parsed);
				}
				// Same relative in multiple segments accumulates conditions on one object.
				parsed.addCondition(entry.condition);
			}
		}
		return new ArrayList<ParsedFamilyHistoryRelative>(byRelative.values());
	}
	
	private static boolean isNoneCondition(String condition) {
		return "none".equalsIgnoreCase(condition.trim().replace(".", ""));
	}
	
	/** One parsed sentence: disease/condition plus the relatives who have it. */
	private static final class Entry {
		
		final String condition;
		
		final List<String> relatives;
		
		Entry(String condition, List<String> relatives) {
			this.condition = condition;
			this.relatives = relatives;
		}
	}
	
}
