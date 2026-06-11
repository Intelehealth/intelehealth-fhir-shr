package org.openmrs.module.ihshr.fhir;

import org.hl7.fhir.r4.model.Condition;

public class DiagnosisBuildResult {
	
	private final Condition condition;
	
	private final Integer rank;
	
	public DiagnosisBuildResult(Condition condition, Integer rank) {
		this.condition = condition;
		this.rank = rank;
	}
	
	public Condition getCondition() {
		return condition;
	}
	
	public Integer getRank() {
		return rank;
	}
}
