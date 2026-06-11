package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.FollowUpParser;

public final class FollowUpMatcher {
	
	private FollowUpMatcher() {
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId, StructuredObsConceptSettings.followUpConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (StringUtils.isBlank(valueText) || FollowUpParser.isDenied(valueText)) {
			return false;
		}
		return new FollowUpParser().parse(valueText) != null;
	}
	
	public static boolean isFollowUpObs(CompletedRecord record) {
		if (record == null) {
			return false;
		}
		if (!matchesConceptId(record.getConceptId())) {
			return false;
		}
		return matchesValueText(record.getValueText());
	}
	
	public static String describeMatch(CompletedRecord record) {
		if (record == null) {
			return "no-record";
		}
		if (!matchesConceptId(record.getConceptId())) {
			return "none";
		}
		if (FollowUpParser.isDenied(record.getValueText())) {
			return "follow-up-denied-no";
		}
		if (matchesValueText(record.getValueText())) {
			return "follow-up-concept-163345";
		}
		return "follow-up-unparseable";
	}
	
}
