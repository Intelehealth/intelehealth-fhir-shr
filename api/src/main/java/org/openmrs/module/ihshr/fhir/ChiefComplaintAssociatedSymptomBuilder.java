package org.openmrs.module.ihshr.fhir;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;

public class ChiefComplaintAssociatedSymptomBuilder {
	
	private static final String CHIEF_COMPLAINT_MAPPINGS = "chief-complaint-mappings.json";
	
	public Observation buildPositive(Observation source, String obsUuid, int index, String symptomText,
	        List<Reference> conditionFocus, String sharedNote) {
		return build(source, obsUuid, ChiefComplaintConstants.associatedPositiveIdentifier(obsUuid, index), symptomText,
		    conditionFocus, sharedNote, true);
	}
	
	public Observation buildNegative(Observation source, String obsUuid, int index, String symptomText,
	        List<Reference> conditionFocus, String sharedNote) {
		return build(source, obsUuid, ChiefComplaintConstants.associatedNegativeIdentifier(obsUuid, index), symptomText,
		    conditionFocus, sharedNote, false);
	}
	
	private Observation build(Observation source, String obsUuid, String identifierValue, String symptomText,
	        List<Reference> conditionFocus, String sharedNote, boolean positive) {
		Observation observation = source == null ? new Observation() : source.copy();
		observation.setId((String) null);
		observation.getIdentifier().clear();
		
		Identifier identifier = new Identifier();
		identifier.setSystem(ChiefComplaintConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(identifierValue);
		observation.addIdentifier(identifier);
		
		observation.setStatus(ObservationStatus.FINAL);
		
		CodeableConcept category = new CodeableConcept();
		category.addCoding(new Coding().setSystem(ChiefComplaintConstants.ASSOC_OBS_CATEGORY_SYSTEM).setCode(
		    ChiefComplaintConstants.ASSOC_OBS_CATEGORY_CODE));
		observation.getCategory().clear();
		observation.addCategory(category);
		
		CodeableConcept code = new CodeableConcept();
		code.setText(symptomText);
		String[] snomed = snomedFor(symptomText);
		if (snomed != null) {
			code.addCoding(new Coding().setSystem(ChiefComplaintConstants.SNOMED_SYSTEM).setCode(snomed[0])
			        .setDisplay(snomed[1]));
		}
		observation.setCode(code);
		
		CodeableConcept value = new CodeableConcept();
		if (positive) {
			value.addCoding(new Coding().setSystem(ChiefComplaintConstants.SNOMED_SYSTEM)
			        .setCode(ChiefComplaintConstants.PRESENT_CODE).setDisplay(ChiefComplaintConstants.PRESENT_DISPLAY));
		} else {
			value.addCoding(new Coding().setSystem(ChiefComplaintConstants.SNOMED_SYSTEM)
			        .setCode(ChiefComplaintConstants.NEGATIVE_CODE).setDisplay(ChiefComplaintConstants.NEGATIVE_DISPLAY));
		}
		observation.setValue(value);
		
		CodeableConcept interpretation = new CodeableConcept();
		interpretation.addCoding(new Coding().setSystem(ChiefComplaintConstants.INTERPRETATION_SYSTEM)
		        .setCode(positive ? ChiefComplaintConstants.INTERPRETATION_POS : ChiefComplaintConstants.INTERPRETATION_NEG)
		        .setDisplay(positive ? "Positive" : "Negative"));
		observation.getInterpretation().clear();
		observation.addInterpretation(interpretation);
		
		observation.getFocus().clear();
		if (conditionFocus != null) {
			for (Reference focusRef : conditionFocus) {
				observation.addFocus(focusRef.copy());
			}
		}
		
		if (source != null) {
			if (source.hasSubject()) {
				observation.setSubject(source.getSubject().copy());
			}
			if (source.hasEncounter()) {
				observation.setEncounter(source.getEncounter().copy());
			}
			if (source.hasEffective()) {
				observation.setEffective(source.getEffective().copy());
			} else if (source.hasIssued()) {
				observation.setEffective(new DateTimeType(source.getIssued()));
			}
			observation.getPerformer().clear();
			if (source.hasPerformer()) {
				observation.getPerformer().addAll(source.getPerformer());
			}
		}
		
		observation.getNote().clear();
		if (StringUtils.isNotBlank(sharedNote)) {
			observation.addNote().setText(sharedNote);
		}
		
		return observation;
	}
	
	private static String[] snomedFor(String symptomText) {
		if (symptomText == null) {
			return null;
		}
		org.hl7.fhir.r4.model.Coding mapped = ClinicalTermCodingResolver.resolveMapping(symptomText.trim(),
		    CHIEF_COMPLAINT_MAPPINGS, UnmappedTermArtifact.CHIEF_COMPLAINT);
		if (mapped != null) {
			return new String[] { mapped.getCode(), mapped.getDisplay() };
		}
		if ("anorexia".equalsIgnoreCase(symptomText.trim())) {
			return new String[] { ChiefComplaintConstants.ANOREXIA_CODE, ChiefComplaintConstants.ANOREXIA_DISPLAY };
		}
		return null;
	}
	
}
