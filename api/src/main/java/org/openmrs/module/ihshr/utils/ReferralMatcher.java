package org.openmrs.module.ihshr.utils;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.ihshr.config.StructuredObsConceptSettings;
import org.openmrs.module.ihshr.domain.CompletedRecord;
import org.openmrs.module.ihshr.parser.ReferralParser;

public final class ReferralMatcher {
	
	private ReferralMatcher() {
	}
	
	public static boolean matchesConceptId(Integer conceptId) {
		return StructuredObsConceptSettings.matchesConceptId(conceptId, StructuredObsConceptSettings.referralConceptIds());
	}
	
	public static boolean matchesValueText(String valueText) {
		if (StringUtils.isBlank(valueText)) {
			return false;
		}
		return !new ReferralParser().parse(valueText).isEmpty();
	}
	
	public static boolean isReferralObs(CompletedRecord record) {
		return record != null && matchesConceptId(record.getConceptId());
	}
	
	public static String describeMatch(CompletedRecord record) {
		if (record == null) {
			return "no-record";
		}
		if (matchesConceptId(record.getConceptId())) {
			return "referral-concept-165238";
		}
		if (matchesValueText(record.getValueText())) {
			return "referral-value_text";
		}
		return "none";
	}
	
}
