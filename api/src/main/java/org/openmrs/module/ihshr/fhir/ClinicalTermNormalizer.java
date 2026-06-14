package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.parser.PhysicalExamValueTexts;

/**
 * Normalizes free-text clinical terms before concept-dictionary lookup (Layer 1).
 */
public final class ClinicalTermNormalizer {
	
	private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[*\\s.,;:?]+$");
	
	private static final Pattern PARENTHETICAL_SUFFIX = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
	
	private ClinicalTermNormalizer() {
	}
	
	public static List<String> lookupVariants(String rawTerm) {
		Set<String> variants = new LinkedHashSet<String>();
		if (StringUtils.isBlank(rawTerm)) {
			return new ArrayList<String>(variants);
		}
		addVariant(variants, rawTerm);
		String stripped = PhysicalExamValueTexts.stripPictureTakenMarkers(rawTerm.trim());
		addVariant(variants, stripped);
		String noPunct = TRAILING_PUNCTUATION.matcher(stripped).replaceAll("").trim();
		addVariant(variants, noPunct);
		String noParen = PARENTHETICAL_SUFFIX.matcher(noPunct).replaceAll("").trim();
		addVariant(variants, noParen);
		int colon = noParen.lastIndexOf(':');
		if (colon >= 0 && colon < noParen.length() - 1) {
			addVariant(variants, noParen.substring(colon + 1).trim());
		}
		return new ArrayList<String>(variants);
	}
	
	private static void addVariant(Set<String> variants, String value) {
		if (StringUtils.isBlank(value)) {
			return;
		}
		String trimmed = value.trim();
		variants.add(trimmed);
		variants.add(trimmed.toLowerCase(Locale.ROOT));
	}
	
}
