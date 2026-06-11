package org.openmrs.module.ihshr.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.domain.ParsedExamCategory;
import org.openmrs.module.ihshr.domain.ParsedFinding;

/**
 * Parses physical examination {@code obs.value_text} as {@code "en":"..."} JSON or plain HTML with
 * {@code <b>Category:</b>} headings. Findings may use bullet lines ({@code •}) or {@code <br/>}
 * -separated plain lines (e.g. {@code Eyes: Jaundice-}, {@code In-person consultation.}).
 */
public class PhysicalExamParser {
	
	private static final Pattern CATEGORY_HEADING = Pattern.compile("<b>\\s*([^<]+?)\\s*:?\\s*</b>",
	    Pattern.CASE_INSENSITIVE);
	
	private static final Pattern BR_SPLIT = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern LEADING_BULLET = Pattern.compile("^•\\s*");
	
	private static final Pattern LEADING_QUESTION_MARK = Pattern.compile("^\\?\\s*");
	
	private static final Pattern TRAILING_DELIMITER_HYPHENS = Pattern.compile("-+$");
	
	/**
	 * Spaced hyphen/em-dash (e.g. {@code Arm-Pinch skin* - appears slow}) — matched first so
	 * compound names like {@code Arm-Pinch} are not broken.
	 */
	private static final Pattern SPACED_ITEM_FINDING_SPLIT = Pattern.compile("\\s+[-–—]\\s+");
	
	/**
	 * Unspaced item-finding delimiter per spec §7.2 (e.g. {@code Jaundice-no jaundice seen},
	 * {@code anemia-Nails are not pale}). Requires {@code :} or a long left segment so
	 * {@code In-person} and {@code Arm-Pinch} stay intact.
	 */
	private static final Pattern UNSPACED_ITEM_FINDING_SPLIT = Pattern.compile("(?<=[a-zA-Z])-(?=[a-zA-Z])");
	
	public List<ParsedExamCategory> parse(String raw) {
		if (StringUtils.isBlank(raw)) {
			return new ArrayList<ParsedExamCategory>();
		}
		
		String text = unescapeHtml(PhysicalExamValueTexts.normalizeHtml(PhysicalExamValueTexts.extractClinicalHtml(raw)));
		Matcher m = CATEGORY_HEADING.matcher(text);
		List<int[]> headings = new ArrayList<int[]>();
		while (m.find()) {
			headings.add(new int[] { m.start(), m.end() });
		}
		
		List<ParsedExamCategory> out = new ArrayList<ParsedExamCategory>();
		for (int i = 0; i < headings.size(); i++) {
			int contentStart = headings.get(i)[1];
			int contentEnd = (i + 1 < headings.size()) ? headings.get(i + 1)[0] : text.length();
			String catName = extractGroup1(text, headings.get(i)[0], contentStart, CATEGORY_HEADING);
			String body = text.substring(contentStart, contentEnd);
			
			List<ParsedFinding> findings = new ArrayList<ParsedFinding>();
			for (String line : BR_SPLIT.split(body)) {
				ParsedFinding finding = parseFindingLine(line);
				if (finding != null) {
					findings.add(finding);
				}
			}
			out.add(new ParsedExamCategory(catName, findings));
		}
		return out;
	}
	
	private static ParsedFinding parseFindingLine(String rawLine) {
		if (StringUtils.isBlank(rawLine)) {
			return null;
		}
		String line = LEADING_BULLET.matcher(rawLine.trim()).replaceFirst("");
		line = LEADING_QUESTION_MARK.matcher(line).replaceFirst("");
		line = PhysicalExamValueTexts.stripPictureTakenMarkers(line);
		line = line.replaceAll("[*\\s.,;]+$", "").trim();
		line = TRAILING_DELIMITER_HYPHENS.matcher(line).replaceAll("").trim();
		if (line.isEmpty() || "?".equals(line)) {
			return null;
		}
		if (CATEGORY_HEADING.matcher(line).find()) {
			return null;
		}
		
		String item;
		String finding;
		String[] split = SPACED_ITEM_FINDING_SPLIT.split(line, 2);
		if (split.length == 2 && StringUtils.isNotBlank(split[0]) && StringUtils.isNotBlank(split[1])) {
			item = split[0].trim();
			finding = split[1].trim();
		} else {
			Matcher hyphen = UNSPACED_ITEM_FINDING_SPLIT.matcher(line);
			if (hyphen.find()) {
				String left = line.substring(0, hyphen.start()).trim();
				if (left.length() > 4 || left.contains(":")) {
					item = left;
					finding = line.substring(hyphen.end()).trim();
				} else {
					item = line;
					finding = "";
				}
			} else {
				item = line;
				finding = "";
			}
		}
		finding = finding.replaceAll("\\.+$", "").trim();
		if (item.isEmpty()) {
			return null;
		}
		return new ParsedFinding(item, finding);
	}
	
	private static String extractGroup1(String text, int regionStart, int regionEnd, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		matcher.region(regionStart, regionEnd);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return "";
	}
	
	private static String unescapeHtml(String raw) {
		return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"")
		        .replace("&#39;", "'");
	}
	
}
