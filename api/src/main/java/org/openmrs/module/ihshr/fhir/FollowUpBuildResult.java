package org.openmrs.module.ihshr.fhir;

import org.hl7.fhir.r4.model.Observation;

public class FollowUpBuildResult {
	
	private final Observation observation;
	
	public FollowUpBuildResult(Observation observation) {
		this.observation = observation;
	}
	
	public Observation getObservation() {
		return observation;
	}
	
	public boolean hasObservation() {
		return observation != null;
	}
	
}
