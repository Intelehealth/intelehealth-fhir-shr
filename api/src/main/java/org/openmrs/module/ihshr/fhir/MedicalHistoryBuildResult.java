package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;

public class MedicalHistoryBuildResult {
	
	private final List<Observation> observations;
	
	private final List<AllergyIntolerance> allergyIntolerances;
	
	private final List<MedicationStatement> medicationStatements;
	
	private final List<Condition> conditions;
	
	public MedicalHistoryBuildResult(List<Observation> observations, List<AllergyIntolerance> allergyIntolerances,
	    List<MedicationStatement> medicationStatements, List<Condition> conditions) {
		this.observations = copy(observations);
		this.allergyIntolerances = copy(allergyIntolerances);
		this.medicationStatements = copy(medicationStatements);
		this.conditions = copy(conditions);
	}
	
	private static <T> List<T> copy(List<T> list) {
		return list == null ? Collections.<T> emptyList() : new ArrayList<T>(list);
	}
	
	public List<Observation> getObservations() {
		return Collections.unmodifiableList(observations);
	}
	
	public List<AllergyIntolerance> getAllergyIntolerances() {
		return Collections.unmodifiableList(allergyIntolerances);
	}
	
	public List<MedicationStatement> getMedicationStatements() {
		return Collections.unmodifiableList(medicationStatements);
	}
	
	public List<Condition> getConditions() {
		return Collections.unmodifiableList(conditions);
	}
	
	public int totalResourceCount() {
		return observations.size() + allergyIntolerances.size() + medicationStatements.size() + conditions.size();
	}
	
	public static MedicalHistoryBuildResult empty() {
		return new MedicalHistoryBuildResult(null, null, null, null);
	}
	
}
