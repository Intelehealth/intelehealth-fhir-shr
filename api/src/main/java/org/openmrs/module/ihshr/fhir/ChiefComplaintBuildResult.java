package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;

/**
 * Output of {@link ChiefComplaintTransfer#build}: FHIR resources to PUT for one chief-complaint
 * obs, plus a flag when associated symptoms were parsed without any chief-complaint
 * {@link Condition}s.
 */
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
	
	/** One {@code encounter-diagnosis} Condition per parsed chief-complaint symptom. */
	public List<Condition> getConditions() {
		return conditions;
	}
	
	/**
	 * Positive/negative associated-symptom Observations; {@code focus} references
	 * {@link #getConditions()}.
	 */
	public List<Observation> getAssociatedSymptomObservations() {
		return associatedSymptomObservations;
	}
	
	/**
	 * {@code true} when associated symptoms exist but {@link #getConditions()} is empty (unusual
	 * source text).
	 */
	public boolean isOrphanAssociatedSymptoms() {
		return orphanAssociatedSymptoms;
	}
	
	/** Sum of conditions and associated-symptom observations; used to skip empty builds upstream. */
	public int totalResourceCount() {
		return conditions.size() + associatedSymptomObservations.size();
	}
	
}
