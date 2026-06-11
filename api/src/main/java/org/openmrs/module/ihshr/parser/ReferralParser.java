package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedReferral;

/**
 * Parses referral obs.value_text (concept 165238). Examples:
 * <ul>
 * <li>{@code General Physician::Elective:sdasfa}</li>
 * <li>{@code Obstetrician & Gynecologist:PHC:Urgent:4ttr}</li>
 * </ul>
 */
public class ReferralParser {
	
	private static final String LINE_SPLIT = "(<br\\s*/?>|\\r?\\n)+";
	
	public List<ParsedReferral> parse(String rawValueText) {
		String clinical = ClinicalJsonValueTexts.extractClinicalHtml(StringUtils.trimToEmpty(rawValueText));
		if (clinical.isEmpty()) {
			return new ArrayList<ParsedReferral>();
		}
		String[] lines = clinical.split(LINE_SPLIT);
		List<ParsedReferral> referrals = new ArrayList<ParsedReferral>();
		int index = 0;
		for (String line : lines) {
			ParsedReferral parsed = parseLine(line);
			if (parsed == null || StringUtils.isBlank(parsed.getSpecialty())) {
				continue;
			}
			parsed.setIndex(index++);
			referrals.add(parsed);
		}
		return referrals;
	}
	
	private ParsedReferral parseLine(String rawLine) {
		String line = StringUtils.trimToEmpty(rawLine);
		if (line.isEmpty()) {
			return null;
		}
		line = line.replace("::", ":");
		String[] parts = line.split(":", -1);
		if (parts.length < 2) {
			ParsedReferral single = new ParsedReferral();
			single.setSpecialty(line);
			return single;
		}
		ParsedReferral parsed = new ParsedReferral();
		parsed.setSpecialty(StringUtils.trimToEmpty(parts[0]));
		if (parts.length >= 4) {
			parsed.setCategory(StringUtils.trimToEmpty(parts[1]));
			parsed.setPriorityText(StringUtils.trimToEmpty(parts[2]));
			parsed.setNotes(joinTail(parts, 3));
			return parsed;
		}
		if (parts.length == 3) {
			String middle = StringUtils.trimToEmpty(parts[1]);
			if (isPriorityToken(middle)) {
				parsed.setPriorityText(middle);
				parsed.setNotes(StringUtils.trimToEmpty(parts[2]));
			} else {
				parsed.setCategory(middle);
				parsed.setNotes(StringUtils.trimToEmpty(parts[2]));
			}
			return parsed;
		}
		parsed.setNotes(StringUtils.trimToEmpty(parts[1]));
		return parsed;
	}
	
	private static String joinTail(String[] parts, int start) {
		StringBuilder builder = new StringBuilder();
		for (int i = start; i < parts.length; i++) {
			if (i > start) {
				builder.append(':');
			}
			builder.append(parts[i]);
		}
		return builder.toString().trim();
	}
	
	static boolean isPriorityToken(String value) {
		if (StringUtils.isBlank(value)) {
			return false;
		}
		String token = value.trim().toLowerCase(Locale.ROOT);
		return "urgent".equals(token) || "routine".equals(token) || "asap".equals(token) || "stat".equals(token);
	}
	
}
