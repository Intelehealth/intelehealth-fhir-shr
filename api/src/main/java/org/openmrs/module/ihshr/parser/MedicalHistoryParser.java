package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedMedicalHistoryTopic;

/**
 * Parses patient medical history obs text (concept 163210) into {@code Topic - Value} rows for
 * {@link org.openmrs.module.ihshr.fhir.MedicalHistoryTransfer}.
 * <p>
 * Accepts multilingual JSON ({@code "en":"..."}) or plain HTML. Each line must match
 * {@code Topic - Value} (e.g. {@code Smoking - Since - 5 Years.}). Topics are keyed by a normalized
 * label so duplicates in the source collapse to one {@link ParsedMedicalHistoryTopic}.
 */
public class MedicalHistoryParser {
	
	private static final Pattern HTML_BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	
	/** Matches {@code • Topic - Value} up to the next {@code <br/>} or end of string. */
	private static final Pattern BULLET_LINE = Pattern.compile("•\\s*([^•]+?)(?=<br\\s*/?>|$)", Pattern.CASE_INSENSITIVE
	        | Pattern.DOTALL);
	
	private static final Pattern LEADING_LINE_MARKER = Pattern.compile("^[?•]\\s*");
	
	/** {@code Topic - Value} with optional trailing period. */
	private static final Pattern TOPIC_VALUE = Pattern.compile("^\\s*(.+?)\\s*-\\s*(.+?)\\s*\\.?\\s*$", Pattern.DOTALL);
	
	public List<ParsedMedicalHistoryTopic> parse(String raw) {
		String html = ClinicalJsonValueTexts.normalizeHtml(ClinicalJsonValueTexts.extractClinicalHtml(raw));
		// One topic per normalized key; LinkedHashMap keeps questionnaire order.
		Map<String, ParsedMedicalHistoryTopic> byTopicKey = new LinkedHashMap<String, ParsedMedicalHistoryTopic>();
		
		// Pass 1: bullet segments (•) common in JSON "en" clinical text.
		Matcher lineMatcher = BULLET_LINE.matcher(html);
		while (lineMatcher.find()) {
			addTopicFromLine(lineMatcher.group(1), byTopicKey);
		}
		
		// Pass 2: line-based parse for plain HTML using ? / • prefixes and <br/> separators.
		String flattened = HTML_BR.matcher(html).replaceAll("\n");
		for (String segment : flattened.split("\n")) {
			addTopicFromLine(segment, byTopicKey);
		}
		
		return new ArrayList<ParsedMedicalHistoryTopic>(byTopicKey.values());
	}
	
	/**
	 * Parses one line into {@code topicLabel} / {@code value}; first occurrence of each topic key
	 * wins.
	 */
	private static void addTopicFromLine(String rawLine, Map<String, ParsedMedicalHistoryTopic> byTopicKey) {
		if (StringUtils.isBlank(rawLine)) {
			return;
		}
		String line = LEADING_LINE_MARKER.matcher(rawLine.trim()).replaceFirst("");
		if (line.isEmpty()) {
			return;
		}
		Matcher tv = TOPIC_VALUE.matcher(line);
		if (!tv.matches()) {
			return;
		}
		String topicLabel = tv.group(1).trim();
		String value = tv.group(2).trim();
		String topicKey = normalizeTopicKey(topicLabel);
		if (!byTopicKey.containsKey(topicKey)) {
			byTopicKey.put(topicKey, new ParsedMedicalHistoryTopic(topicKey, topicLabel, value));
		}
	}
	
	/**
	 * Stable key for {@code patient-history-topics.json} lookup (strip *, lowercase, collapse
	 * spaces).
	 */
	public static String normalizeTopicKey(String topicLabel) {
		if (topicLabel == null) {
			return "";
		}
		return topicLabel.replace("*", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
	
	/**
	 * Splits a medical-history topic value into individual past diseases for multiple Condition
	 * resources.
	 * <p>
	 * Comma-split only when the value looks like a simple list ({@code Diabetes, Hypertension});
	 * values that contain {@code " - "} (e.g. smoking duration) stay as one string.
	 */
	public static List<String> splitMedicalHistoryConditions(String value) {
		if (StringUtils.isBlank(value)) {
			return new ArrayList<String>();
		}
		String v = value.trim();
		if (v.endsWith(".")) {
			v = v.substring(0, v.length() - 1).trim();
		}
		if ("none".equalsIgnoreCase(v)) {
			return new ArrayList<String>();
		}
		if (!v.contains(",")) {
			return Arrays.asList(v);
		}
		List<String> parts = Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty())
		        .collect(Collectors.toList());
		boolean simpleList = true;
		for (String part : parts) {
			if (part.split(" - ", -1).length > 2) {
				simpleList = false;
				break;
			}
		}
		if (simpleList && parts.size() > 1) {
			return parts;
		}
		return Arrays.asList(v);
	}
	
	/**
	 * Single-topic note for one derived FHIR resource (doc §7.4). Only the positive line that drove
	 * emission — not the full obs.value_text and not other positive topics.
	 */
	public static String buildTopicNoteText(ParsedMedicalHistoryTopic topic) {
		if (topic == null || StringUtils.isBlank(topic.getTopicLabel())) {
			return "";
		}
		return topic.getTopicLabel() + " - " + topic.getValue() + ".";
	}
	
	/** Concatenated topic notes for all positive topics (used when a combined note is needed). */
	public static String buildPositiveNoteText(List<ParsedMedicalHistoryTopic> positiveTopics) {
		if (positiveTopics == null || positiveTopics.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (ParsedMedicalHistoryTopic topic : positiveTopics) {
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(buildTopicNoteText(topic));
		}
		return sb.toString();
	}
	
}
