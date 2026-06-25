package org.openmrs.module.ihshr.parser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedFollowUp;

/**
 * Parses follow-up obs {@code value_text} (concept 163345) into {@link ParsedFollowUp} for
 * {@link org.openmrs.module.ihshr.fhir.FollowUpTransfer}.
 * <p>
 * Examples:
 * <ul>
 * <li>Scheduled: {@code 2026-06-11,Time:10:00 AM,Remark:NA,Type:In person} — leading ISO date, then
 * comma-separated {@code Key:Value} fields ({@code Time}, {@code Remark}, {@code Type}).</li>
 * <li>JSON wrapper: {@code "en":"2026-06-11,Time:10:00 AM,Remark:NA,Type:In person"} — clinical
 * text is taken from {@code en} via {@link ClinicalJsonValueTexts}.</li>
 * <li>Not scheduled: {@code No} (case-insensitive) — returns {@code null}; nothing is pushed.</li>
 * </ul>
 */
public class FollowUpParser {
	
	private static final Pattern DATE_TOKEN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
	
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
	
	/**
	 * Returns {@code null} when the patient declined follow-up ({@code No}) or no valid date is
	 * present.
	 */
	public ParsedFollowUp parse(String rawValueText) {
		String clinical = ClinicalJsonValueTexts.extractClinicalHtml(StringUtils.trimToEmpty(rawValueText));
		if (clinical.isEmpty() || isDenied(clinical)) {
			return null;
		}
		// "2026-06-11,Time:10:00 AM,Remark:NA,Type:In person"
		String[] parts = clinical.split(",");
		if (parts.length == 0) {
			return null;
		}
		ParsedFollowUp parsed = new ParsedFollowUp();
		int index = 0;
		String first = StringUtils.trimToEmpty(parts[0]);
		if (DATE_TOKEN.matcher(first).matches()) {
			parsed.setDateText(first);
			index = 1;
		}
		for (int i = index; i < parts.length; i++) {
			applyField(parsed, parts[i]);
		}
		if (StringUtils.isBlank(parsed.getDateText())) {
			return null;
		}
		parsed.setScheduledDateTime(resolveScheduledDateTime(parsed));
		return parsed;
	}
	
	/** {@code true} when UI stored a lone {@code No} (patient not scheduled for follow-up). */
	public static boolean isDenied(String rawValueText) {
		String clinical = ClinicalJsonValueTexts.extractClinicalHtml(StringUtils.trimToEmpty(rawValueText));
		return "no".equalsIgnoreCase(clinical);
	}
	
	/**
	 * Maps {@code Time:10:00 AM}, {@code Remark:NA}, {@code Type:In person} onto
	 * {@link ParsedFollowUp}.
	 */
	private static void applyField(ParsedFollowUp parsed, String segment) {
		String token = StringUtils.trimToEmpty(segment);
		if (token.isEmpty()) {
			return;
		}
		int colon = token.indexOf(':');
		if (colon < 0) {
			if (parsed.getDateText() == null && DATE_TOKEN.matcher(token).matches()) {
				parsed.setDateText(token);
			}
			return;
		}
		String key = token.substring(0, colon).trim();
		String value = token.substring(colon + 1).trim();
		if ("Time".equalsIgnoreCase(key)) {
			parsed.setTimeText(value);
		} else if ("Remark".equalsIgnoreCase(key)) {
			parsed.setRemark(value);
		} else if ("Type".equalsIgnoreCase(key)) {
			parsed.setVisitType(value);
		}
	}
	
	/**
	 * Combines {@code dateText} + {@code timeText} in the JVM default zone; midnight when time is
	 * absent.
	 */
	private static Date resolveScheduledDateTime(ParsedFollowUp parsed) {
		try {
			LocalDate date = LocalDate.parse(parsed.getDateText(), DATE_FORMAT);
			LocalTime time = parseTime(parsed.getTimeText());
			if (time == null) {
				time = LocalTime.MIDNIGHT;
			}
			return Date.from(date.atTime(time).atZone(ZoneId.systemDefault()).toInstant());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}
	
	/**
	 * Parses UI time strings such as {@code 10:00 AM} or {@code 14:30}; returns {@code null} when
	 * unrecognized.
	 */
	static LocalTime parseTime(String timeText) {
		if (StringUtils.isBlank(timeText)) {
			return null;
		}
		String normalized = timeText.trim().replaceAll("\\s+", " ");
		for (String pattern : new String[] { "h:mm a", "hh:mm a", "H:mm", "HH:mm" }) {
			try {
				return LocalTime.parse(normalized, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
			}
			catch (DateTimeParseException ignored) {
				// try next pattern
			}
		}
		return null;
	}
	
}
