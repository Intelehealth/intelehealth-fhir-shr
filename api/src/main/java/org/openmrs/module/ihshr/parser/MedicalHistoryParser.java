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
 * Parses patient medical history clinical HTML into {@code Topic - Value} lines from
 * {@code "en":"..."} JSON or plain HTML ({@code •} / {@code ?} bullet lines separated by
 * {@code <br/>}).
 */
public class MedicalHistoryParser {
	
	private static final Pattern HTML_BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BULLET_LINE = Pattern.compile("•\\s*([^•]+?)(?=<br\\s*/?>|$)", Pattern.CASE_INSENSITIVE
	        | Pattern.DOTALL);
	
	private static final Pattern LEADING_LINE_MARKER = Pattern.compile("^[?•]\\s*");
	
	private static final Pattern TOPIC_VALUE = Pattern.compile("^\\s*(.+?)\\s*-\\s*(.+?)\\s*\\.?\\s*$", Pattern.DOTALL);
	
	public List<ParsedMedicalHistoryTopic> parse(String raw) {
		String html = ClinicalJsonValueTexts.normalizeHtml(ClinicalJsonValueTexts.extractClinicalHtml(raw));
		Map<String, ParsedMedicalHistoryTopic> byTopicKey = new LinkedHashMap<String, ParsedMedicalHistoryTopic>();
		
		Matcher lineMatcher = BULLET_LINE.matcher(html);
		while (lineMatcher.find()) {
			addTopicFromLine(lineMatcher.group(1), byTopicKey);
		}
		
		String flattened = HTML_BR.matcher(html).replaceAll("\n");
		for (String segment : flattened.split("\n")) {
			addTopicFromLine(segment, byTopicKey);
		}
		
		return new ArrayList<ParsedMedicalHistoryTopic>(byTopicKey.values());
	}
	
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
	
	public static String normalizeTopicKey(String topicLabel) {
		if (topicLabel == null) {
			return "";
		}
		return topicLabel.replace("*", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
	
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
