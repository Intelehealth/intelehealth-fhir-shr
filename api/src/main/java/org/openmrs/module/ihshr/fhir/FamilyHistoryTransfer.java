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

/**
 * Builds SHR-ready {@link FamilyMemberHistory} resources from one OpenMRS obs (concept 163211).
 * Called by {@code DataSendToSHR.addFamilyHistoryToVisitBuilder}.
 * <p>
 * Flow: require multilingual JSON with {@code en} clinical text → {@link FamilyHistoryParser}
 * inverts condition-first UI lines into relative-first rows → one {@link FamilyMemberHistory} per
 * family member.
 * <p>
 * Examples (UI stores {@code Condition, Relative[, Relative]} segments separated by periods):
 * <ul>
 * <li>{@code "en":"•Do you have a family history... : High BP, Mother. Diabetes, Mother."} → one
 * {@code FamilyMemberHistory} for Mother with conditions High BP and Diabetes.</li>
 * <li>{@code "en":"... High BP, आजोबा, father. Diabetes, आजोबा."} → grandfather (आजोबा) with High
 * BP + Diabetes; father with High BP only.</li>
 * <li>{@code "en":"... None."} or plain HTML without {@code en} JSON → empty result, nothing
 * pushed.</li>
 * </ul>
 */
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
			// One FamilyMemberHistory per relative; conditions[] holds all diseases for that person.
			resources.add(builder.build(sourceObs, obsUuid, relative, sharedNote));
		}
		return new FamilyHistoryBuildResult(resources);
	}
	
	/** Flattened clinical HTML from {@code en} JSON for {@code FamilyMemberHistory.note}. */
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
