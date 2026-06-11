/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ihshr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;
import org.openmrs.module.ihshr.event.EncounterCreatedEventSubscription;

/**
 * Lifecycle hooks for the IHSHR OpenMRS module (OMOD).
 */
public class IhshrModuleActivator extends BaseModuleActivator implements DaemonTokenAware {
	
	private final Log log = LogFactory.getLog(getClass());
	
	private static volatile DaemonToken daemonToken;
	
	@Override
	public void started() {
		log.info("Started IHSHR module");
		EncounterCreatedEventSubscription.register();
	}
	
	@Override
	public void stopped() {
		EncounterCreatedEventSubscription.unregister();
		daemonToken = null;
		log.info("Stopped IHSHR module");
	}
	
	@Override
	public void setDaemonToken(DaemonToken token) {
		daemonToken = token;
	}
	
	public static DaemonToken getDaemonToken() {
		return daemonToken;
	}
}
