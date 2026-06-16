package org.openmrs.module.ihshr.pull;

import org.openmrs.api.context.Context;

/**
 * OpenMRS session auth for SHR pull endpoints (module/ihshr/*.form require JSESSIONID, not Tomcat
 * basic auth).
 */
public final class ShrPullAuthSupport {
	
	public static final String PRIVILEGE_GET_PATIENTS = "Get Patients";
	
	private ShrPullAuthSupport() {
	}
	
	public static void requirePullAccess() {
		if (!Context.isAuthenticated()) {
			throw new ShrPullException(
			        ShrPullErrorCode.UNAUTHORIZED,
			        "Not authenticated. POST /openmrs/ws/rest/v1/session with username/password, then send the JSESSIONID cookie on pull requests.");
		}
		if (!Context.hasPrivilege(PRIVILEGE_GET_PATIENTS)) {
			throw new ShrPullException(ShrPullErrorCode.FORBIDDEN, "Privileges required: " + PRIVILEGE_GET_PATIENTS);
		}
	}
}
