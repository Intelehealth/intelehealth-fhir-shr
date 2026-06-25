package org.openmrs.module.ihshr.pull;

import org.openmrs.api.context.Context;

/**
 * Access rules for SHR pull API (doc §4.2 admin-only local echo).
 */
public final class ShrPullAccessControl {
	
	public static final String PRIVILEGE_LOCAL_ECHO = "Manage IHSHR Pull Debug";
	
	private ShrPullAccessControl() {
	}
	
	public static boolean resolveIncludeLocalEcho(boolean requested) {
		if (!requested) {
			return false;
		}
		if (!Context.isAuthenticated()) {
			throw new ShrPullException(ShrPullErrorCode.FORBIDDEN,
			        "includeLocalEcho requires an authenticated OpenMRS session");
		}
		if (Context.getAuthenticatedUser().isSuperUser() || Context.hasPrivilege(PRIVILEGE_LOCAL_ECHO)) {
			return true;
		}
		throw new ShrPullException(ShrPullErrorCode.FORBIDDEN, "includeLocalEcho requires privilege " + PRIVILEGE_LOCAL_ECHO);
	}
}
