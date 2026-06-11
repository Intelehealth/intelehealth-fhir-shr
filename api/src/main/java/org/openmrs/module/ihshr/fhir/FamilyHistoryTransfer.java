package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;
import org.openmrs.module.ihshr.parser.ClinicalJsonValueTexts;
import org.openmrs.module.ihshr.parser.FamilyHistoryParser;

public class FamilyHistoryTransfer {
	
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	
	private final FamilyHistoryParser parser = new FamilyHistoryParser();
	
	private final FamilyMemberHistoryBuilder builder = new FamilyMemberHistoryBuilder();
	
	public FamilyHistoryBuildResult build(Observation sourceObs, String obsUuid, String valueText) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid) || !ClinicalJsonValueTexts.hasEnClinicalJson(valueText)) {
			return FamilyHistoryBuildResult.empty();
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
		}
		
		List<ParsedFamilyHistoryRelative> relatives = parser.parse(valueText);
		if (relatives.isEmpty()) {
			return FamilyHistoryBuildResult.empty();
		}
		
		String sharedNote = stripHtmlForNote(valueText);
		List<FamilyMemberHistory> resources = new ArrayList<FamilyMemberHistory>();
		for (ParsedFamilyHistoryRelative relative : relatives) {
			resources.add(builder.build(sourceObs, obsUuid, relative, sharedNote));
		}
		return new FamilyHistoryBuildResult(resources);
	}
	
	public static String stripHtmlForNote(String raw) {
		if (StringUtils.isBlank(raw)) {
			return "";
		}
		String clinical = ClinicalJsonValueTexts.normalizeHtml(ClinicalJsonValueTexts.extractClinicalHtml(raw));
		String text = HTML_TAG.matcher(clinical).replaceAll(" ");
		text = text.replace("&nbsp;", " ").replace("<br/>", "\n").replace("<br>", "\n");
		return text.replaceAll("\\s+", " ").trim();
	}
	
}
