package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;

public class ChiefComplaintBuildResult {
	
	private final List<Condition> conditions;
	
	private final List<Observation> associatedSymptomObservations;
	
	private final boolean orphanAssociatedSymptoms;
	
	public ChiefComplaintBuildResult(List<Condition> conditions, List<Observation> associatedSymptomObservations,
	    boolean orphanAssociatedSymptoms) {
		this.conditions = conditions == null ? Collections.<Condition> emptyList() : new ArrayList<Condition>(conditions);
		this.associatedSymptomObservations = associatedSymptomObservations == null ? Collections.<Observation> emptyList()
		        : new ArrayList<Observation>(associatedSymptomObservations);
		this.orphanAssociatedSymptoms = orphanAssociatedSymptoms;
	}
	
	public List<Condition> getConditions() {
		return conditions;
	}
	
	public List<Observation> getAssociatedSymptomObservations() {
		return associatedSymptomObservations;
	}
	
	public boolean isOrphanAssociatedSymptoms() {
		return orphanAssociatedSymptoms;
	}
	
	public int totalResourceCount() {
		return conditions.size() + associatedSymptomObservations.size();
	}
	
}
