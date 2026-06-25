package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedReferral;

/**
 * Parses referral obs {@code value_text} (concept 165238) into {@link ParsedReferral} rows for
 * {@link org.openmrs.module.ihshr.fhir.ReferralTransfer}.
 * <p>
 * Each non-empty line becomes one referral. Lines are split on {@code <br/>} or newlines. Colons
 * separate fields; {@code ::} is normalized to {@code :} before splitting.
 * <p>
 * Examples:
 * <ul>
 * <li>{@code General Physician::Elective:sdasfa} → specialty={@code General Physician}, category=
 * {@code Elective}, notes={@code sdasfa}.</li>
 * <li>{@code Obstetrician & Gynecologist:PHC:Urgent:4ttr} → specialty, category={@code PHC},
 * priority={@code Urgent}, notes={@code 4ttr}.</li>
 * <li>Multi-line: {@code General Physician::Elective:sdasfa<br/>
 * Obstetrician & Gynecologist:PHC:Urgent:4ttr} → two referrals with index 0 and 1.</li>
 * <li>JSON wrapper: {@code "en":"General Physician::Elective:sdasfa"} .</li>
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
	
	/**
	 * Parses one line as {@code specialty[:category][:priority][:notes]}.
	 * <ul>
	 * <li>2 parts — specialty + notes only.</li>
	 * <li>3 parts — if middle token is Urgent/Routine/ASAP/STAT → priority + notes; else category +
	 * notes.</li>
	 * <li>4+ parts — specialty, category, priority, notes (notes may contain extra colons via
	 * {@link #joinTail}).</li>
	 * </ul>
	 */
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
	
	/** Rejoins note text when free text contains colons (e.g. {@code note:with:colons}). */
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
	
	/** Recognizes FHIR ServiceRequest priority tokens in the middle segment of a 3-part line. */
	static boolean isPriorityToken(String value) {
		if (StringUtils.isBlank(value)) {
			return false;
		}
		String token = value.trim().toLowerCase(Locale.ROOT);
		return "urgent".equals(token) || "routine".equals(token) || "asap".equals(token) || "stat".equals(token);
	}
	
}
