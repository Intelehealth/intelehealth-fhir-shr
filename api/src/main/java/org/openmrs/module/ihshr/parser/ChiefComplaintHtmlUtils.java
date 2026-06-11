package org.openmrs.module.ihshr.parser;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public final class ChiefComplaintHtmlUtils {
	
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	
	private ChiefComplaintHtmlUtils() {
	}
	
	public static String stripHtml(String raw) {
		if (StringUtils.isBlank(raw)) {
			return "";
		}
		String text = HTML_TAG.matcher(raw).replaceAll(" ");
		text = text.replace("&nbsp;", " ").replace("<br/>", "\n").replace("<br>", "\n");
		text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		return text.replaceAll("\\s+", " ").trim();
	}
	
}
