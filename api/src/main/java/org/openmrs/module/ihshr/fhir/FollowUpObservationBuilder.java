package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.StringType;
import org.openmrs.module.ihshr.domain.ParsedFollowUp;
import org.openmrs.module.ihshr.utils.FollowUpConstants;

public class FollowUpObservationBuilder {
	
	public Observation build(Observation sourceObs, String obsUuid, ParsedFollowUp parsed) {
		Observation observation = new Observation();
		observation.setId((String) null);
		observation.setStatus(ObservationStatus.FINAL);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(FollowUpConstants.OBS_IDENTIFIER_SYSTEM);
		identifier.setValue(FollowUpConstants.observationIdentifier(obsUuid));
		observation.addIdentifier(identifier);
		
		CodeableConcept category = new CodeableConcept();
		category.addCoding(new Coding().setSystem(FollowUpConstants.CATEGORY_SYSTEM)
		        .setCode(FollowUpConstants.CATEGORY_CODE));
		observation.addCategory(category);
		
		CodeableConcept code = new CodeableConcept();
		code.setText("Follow up date");
		code.addCoding(new Coding().setSystem(FollowUpConstants.CONCEPT_CODE_SYSTEM)
		        .setCode(String.valueOf(FollowUpConstants.FOLLOW_UP_CONCEPT_ID)).setDisplay("Follow up date"));
		code.addCoding(new Coding().setSystem(FollowUpConstants.SNOMED_SYSTEM)
		        .setCode(FollowUpConstants.SNOMED_FOLLOW_UP_CODE).setDisplay(FollowUpConstants.SNOMED_FOLLOW_UP_DISPLAY));
		observation.setCode(code);
		
		if (parsed.getScheduledDateTime() != null) {
			observation.setEffective(new DateTimeType(parsed.getScheduledDateTime()));
		}
		
		if (StringUtils.isNotBlank(parsed.getVisitType())) {
			observation.setValue(new StringType(parsed.getVisitType()));
		}
		
		if (StringUtils.isNotBlank(parsed.getTimeText())) {
			observation.addComponent(component("Time", parsed.getTimeText()));
		}
		if (StringUtils.isNotBlank(parsed.getRemark())) {
			observation.addComponent(component("Remark", parsed.getRemark()));
		}
		if (StringUtils.isNotBlank(parsed.getVisitType())) {
			observation.addComponent(component("Type", parsed.getVisitType()));
		}
		
		if (sourceObs != null) {
			if (sourceObs.hasSubject()) {
				observation.setSubject(sourceObs.getSubject().copy());
			}
			if (sourceObs.hasEncounter()) {
				observation.setEncounter(sourceObs.getEncounter().copy());
			}
			if (sourceObs.hasPerformer()) {
				observation.addPerformer(sourceObs.getPerformerFirstRep().copy());
			}
			if (!observation.hasEffective() && sourceObs.hasEffectiveDateTimeType()) {
				observation.setEffective(sourceObs.getEffectiveDateTimeType().copy());
			}
		}
		
		return observation;
	}
	
	private static ObservationComponentComponent component(String label, String value) {
		ObservationComponentComponent component = new ObservationComponentComponent();
		component.setCode(new CodeableConcept().setText(label));
		component.setValue(new StringType(value));
		return component;
	}
	
}
