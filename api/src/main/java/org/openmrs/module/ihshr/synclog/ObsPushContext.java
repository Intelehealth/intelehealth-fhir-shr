package org.openmrs.module.ihshr.synclog;

/**
 * Visit and trigger encounter for an observation-driven SHR push (doc §3.1 step 9).
 */
public class ObsPushContext {
	
	private final String visitUuid;
	
	private final String triggerEncounterUuid;
	
	private final String obsUuid;
	
	public ObsPushContext(String visitUuid, String triggerEncounterUuid, String obsUuid) {
		this.visitUuid = visitUuid;
		this.triggerEncounterUuid = triggerEncounterUuid;
		this.obsUuid = obsUuid;
	}
	
	public String getVisitUuid() {
		return visitUuid;
	}
	
	public String getTriggerEncounterUuid() {
		return triggerEncounterUuid;
	}
	
	public String getObsUuid() {
		return obsUuid;
	}
}
