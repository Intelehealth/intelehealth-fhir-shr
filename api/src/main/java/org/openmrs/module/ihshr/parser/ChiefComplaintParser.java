package org.openmrs.module.ihshr.parser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedAssociatedSymptoms;
import org.openmrs.module.ihshr.domain.ParsedComplaint;
import org.openmrs.module.ihshr.domain.ParsedComplaintBundle;

/**
 * Parses chief complaint {@code obs.value_text} as raw HTML or {@code "en":"..."} JSON:
 * {@code ►<b>Symptom</b>:} blocks with optional Duration or Since; and an optional
 * {@code Associated symptoms} block.
 */
public class ChiefComplaintParser {
	
	private static final Pattern SYMPTOM_SPLIT = Pattern.compile("►<b>");
	
	private static final Pattern SYMPTOM_NAME = Pattern.compile("^(.*?)</b>", Pattern.DOTALL);
	
	private static final Pattern LABELED_DURATION_LINE = Pattern.compile("•\\s*(?:Duration|Since)\\s*-\\s*([^.<]+)",
	    Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BARE_DURATION_LINE = Pattern.compile("•\\s*(\\d+\\s+(?:Hour|Day|Week|Month|Year)s?)\\.?",
	    Pattern.CASE_INSENSITIVE);
	
	private static final Pattern DURATION_VALUE = Pattern.compile("(\\d+)\\s+(Hour|Day|Week|Month|Year)s?",
	    Pattern.CASE_INSENSITIVE);
	
	private static final Pattern ASSOC_TITLE = Pattern.compile("^\\s*associated\\s+symptoms\\s*:?\\s*$",
	    Pattern.CASE_INSENSITIVE);
	
	private static final Pattern REPORTS_MARKER = Pattern.compile("Patient\\s+reports", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern DENIES_MARKER = Pattern.compile("Patient\\s+denies", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BULLET_LINE = Pattern.compile("•\\s*(.+?)(?=\\s*<br\\s*/?>|►|$)", Pattern.DOTALL);
	
	public ParsedComplaintBundle parse(String raw, Date encounterDate) {
		String text = ChiefComplaintValueTexts.extractClinicalHtml(raw);
		String fullText = ChiefComplaintHtmlUtils.stripHtml(text);
		
		List<ParsedComplaint> complaints = new ArrayList<ParsedComplaint>();
		ParsedAssociatedSymptoms associated = null;
		int idx = 0;
		
		for (String section : SYMPTOM_SPLIT.split(text)) {
			if (StringUtils.isBlank(section)) {
				continue;
			}
			Matcher nameMatcher = SYMPTOM_NAME.matcher(section);
			if (!nameMatcher.find()) {
				continue;
			}
			String title = ChiefComplaintHtmlUtils.stripHtml(nameMatcher.group(1)).trim();
			if (title.isEmpty()) {
				continue;
			}
			
			if (ASSOC_TITLE.matcher(title).matches()) {
				associated = parseAssociatedBlock(section);
			} else {
				ParsedComplaint complaint = new ParsedComplaint(title, fullText, idx++);
				applyDuration(complaint, section, encounterDate);
				complaints.add(complaint);
			}
		}
		
		return new ParsedComplaintBundle(complaints, associated, fullText);
	}
	
	private void applyDuration(ParsedComplaint complaint, String section, Date encounterDate) {
		Matcher labeledMatcher = LABELED_DURATION_LINE.matcher(section);
		if (labeledMatcher.find()) {
			complaint.setDurationText(labeledMatcher.group(1).trim());
			complaint.setOnsetDateTime(parseDurationToOnset(complaint.getDurationText(), encounterDate));
			return;
		}
		Matcher bareMatcher = BARE_DURATION_LINE.matcher(section);
		if (bareMatcher.find()) {
			complaint.setDurationText(bareMatcher.group(1).trim());
			complaint.setOnsetDateTime(parseDurationToOnset(complaint.getDurationText(), encounterDate));
		}
	}
	
	private ParsedAssociatedSymptoms parseAssociatedBlock(String body) {
		List<String> reports = new ArrayList<String>();
		List<String> denies = new ArrayList<String>();
		
		String normalized = body.replaceAll("<br\\s*/?>", "\n").replaceAll("<[^>]+>", " ");
		int deniesIndex = indexOfIgnoreCase(normalized, "patient denies");
		String reportsPart = deniesIndex >= 0 ? normalized.substring(0, deniesIndex) : normalized;
		String deniesPart = deniesIndex >= 0 ? normalized.substring(deniesIndex) : "";
		
		int reportsIndex = indexOfIgnoreCase(reportsPart, "patient reports");
		if (reportsIndex >= 0) {
			reportsPart = reportsPart.substring(reportsIndex);
		}
		reportsPart = stripLeadingMarkerDash(REPORTS_MARKER.matcher(reportsPart).replaceFirst(""));
		deniesPart = stripLeadingMarkerDash(DENIES_MARKER.matcher(deniesPart).replaceFirst(""));
		
		addSymptomTokens(reports, reportsPart);
		addSymptomTokens(denies, deniesPart);
		
		if (reports.isEmpty() && denies.isEmpty()) {
			List<String> currentBucket = null;
			for (String bullet : extractBullets(body)) {
				if (REPORTS_MARKER.matcher(bullet).find()) {
					currentBucket = reports;
					String remainder = REPORTS_MARKER.matcher(bullet).replaceFirst("").trim();
					addSymptomTokens(reports, remainder);
				} else if (DENIES_MARKER.matcher(bullet).find()) {
					currentBucket = denies;
					String remainder = DENIES_MARKER.matcher(bullet).replaceFirst("").trim();
					addSymptomTokens(denies, remainder);
				} else if (currentBucket != null) {
					addSymptomTokens(currentBucket, bullet);
				}
			}
		}
		
		if (reports.isEmpty() && denies.isEmpty()) {
			return null;
		}
		return new ParsedAssociatedSymptoms(reports, denies);
	}
	
	private static void addSymptomTokens(List<String> bucket, String text) {
		if (StringUtils.isBlank(text)) {
			return;
		}
		for (String item : text.split("[,\\n•]")) {
			String cleaned = item.replaceFirst("^\\s*Other\\s*►\\s*", "").trim();
			cleaned = cleaned.replaceFirst("^-\\s*", "").trim();
			cleaned = cleaned.replaceAll("[\\[\\]\\.,;]+$", "").trim();
			if (!cleaned.isEmpty() && !"-".equals(cleaned) && !REPORTS_MARKER.matcher(cleaned).find()
			        && !DENIES_MARKER.matcher(cleaned).find()) {
				bucket.add(cleaned);
			}
		}
	}
	
	private static String stripLeadingMarkerDash(String text) {
		if (StringUtils.isBlank(text)) {
			return text;
		}
		return text.replaceFirst("^\\s*-\\s*", "").trim();
	}
	
	private static int indexOfIgnoreCase(String text, String needle) {
		return text.toLowerCase().indexOf(needle.toLowerCase());
	}
	
	private List<String> extractBullets(String body) {
		List<String> bullets = new ArrayList<String>();
		Matcher matcher = BULLET_LINE.matcher(body);
		while (matcher.find()) {
			String bullet = matcher.group(1).trim();
			if (!bullet.isEmpty()) {
				bullets.add(bullet);
			}
		}
		return bullets;
	}
	
	static Date parseDurationToOnset(String durationText, Date encounterDate) {
		if (StringUtils.isBlank(durationText) || encounterDate == null) {
			return null;
		}
		Matcher matcher = DURATION_VALUE.matcher(durationText);
		if (!matcher.find()) {
			return null;
		}
		int amount = Integer.parseInt(matcher.group(1));
		String unit = matcher.group(2).toLowerCase();
		long days;
		switch (unit) {
			case "hour":
				days = 0;
				break;
			case "day":
				days = amount;
				break;
			case "week":
				days = amount * 7L;
				break;
			case "month":
				days = amount * 30L;
				break;
			case "year":
				days = amount * 365L;
				break;
			default:
				return null;
		}
		ZoneId zone = ZoneId.systemDefault();
		ZonedDateTime encounterZdt = encounterDate.toInstant().atZone(zone);
		if ("hour".equals(unit)) {
			return Date.from(encounterZdt.truncatedTo(ChronoUnit.DAYS).toInstant());
		}
		ZonedDateTime onset = encounterZdt.minusDays(days).truncatedTo(ChronoUnit.DAYS);
		return Date.from(onset.toInstant());
	}
	
}
