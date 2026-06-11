package org.openmrs.module.ihshr.pull;

/**
 * Doc §4.2 / §9 preset pull views.
 */
public enum ShrPullView {
	DEFAULT, PROBLEMS, MEDICATIONS, VITALS, FAMILY_HISTORY, REFERRALS, FOLLOW_UP, DOCUMENTS, CUSTOM;
	
	public static ShrPullView fromParam(String value) {
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT;
		}
		String normalized = value.trim().toLowerCase().replace('-', '_');
		if ("family".equals(normalized) || "familyhistory".equals(normalized)) {
			return FAMILY_HISTORY;
		}
		if ("followup".equals(normalized) || "follow_up_date".equals(normalized)) {
			return FOLLOW_UP;
		}
		for (ShrPullView view : values()) {
			if (view.name().equalsIgnoreCase(normalized)) {
				return view;
			}
		}
		throw new ShrPullException(ShrPullErrorCode.INVALID_FILTER, "Unknown view: " + value);
	}
}
