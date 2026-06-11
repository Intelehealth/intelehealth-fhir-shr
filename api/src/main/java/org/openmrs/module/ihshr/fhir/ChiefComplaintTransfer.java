package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.domain.ParsedAssociatedSymptoms;
import org.openmrs.module.ihshr.domain.ParsedComplaint;
import org.openmrs.module.ihshr.domain.ParsedComplaintBundle;
import org.openmrs.module.ihshr.parser.ChiefComplaintParser;

/**
 * Builds SHR-ready chief-complaint {@link Condition} and associated-symptom {@link Observation}
 * resources.
 */
public class ChiefComplaintTransfer {
	
	private final ChiefComplaintParser parser = new ChiefComplaintParser();
	
	private final ChiefComplaintConditionBuilder conditionBuilder = new ChiefComplaintConditionBuilder();
	
	private final ChiefComplaintAssociatedSymptomBuilder associatedBuilder = new ChiefComplaintAssociatedSymptomBuilder();
	
	public ChiefComplaintBuildResult build(Observation sourceObs, String obsUuid, String valueText) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid)) {
			return empty();
		}
		
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
		}
		if (StringUtils.isBlank(valueText)) {
			return empty();
		}
		
		Date encounterDate = resolveEncounterDate(sourceObs);
		ParsedComplaintBundle bundle = parser.parse(valueText, encounterDate);
		if (bundle.getComplaints().isEmpty()
		        && (bundle.getAssociatedSymptoms() == null || bundle.getAssociatedSymptoms().isEmpty())) {
			return empty();
		}
		
		List<Condition> conditions = new ArrayList<Condition>();
		List<Reference> conditionFocus = new ArrayList<Reference>();
		for (ParsedComplaint complaint : bundle.getComplaints()) {
			Condition condition = conditionBuilder.build(sourceObs, obsUuid, complaint);
			conditions.add(condition);
			conditionFocus.add(new Reference("Condition/" + VisitPushResourceIds.resolvePutResourceId(condition)));
		}
		
		String sharedNote = bundle.getSharedNoteText();
		
		List<Observation> associatedObservations = new ArrayList<Observation>();
		ParsedAssociatedSymptoms associated = bundle.getAssociatedSymptoms();
		boolean orphanAssociated = false;
		if (associated != null && !associated.isEmpty()) {
			if (conditionFocus.isEmpty()) {
				orphanAssociated = true;
			}
			int posIdx = 0;
			for (String report : associated.getReports()) {
				associatedObservations.add(associatedBuilder.buildPositive(sourceObs, obsUuid, posIdx++, report,
				    conditionFocus, sharedNote));
			}
			int negIdx = 0;
			for (String deny : associated.getDenies()) {
				associatedObservations.add(associatedBuilder.buildNegative(sourceObs, obsUuid, negIdx++, deny,
				    conditionFocus, sharedNote));
			}
		}
		
		return new ChiefComplaintBuildResult(conditions, associatedObservations, orphanAssociated);
	}
	
	private static Date resolveEncounterDate(Observation sourceObs) {
		if (sourceObs.hasEffectiveDateTimeType()) {
			return sourceObs.getEffectiveDateTimeType().getValue();
		}
		if (sourceObs.hasIssued()) {
			return sourceObs.getIssued();
		}
		return new Date();
	}
	
	private static ChiefComplaintBuildResult empty() {
		return new ChiefComplaintBuildResult(null, null, false);
	}
	
}
