package org.openmrs.module.ihshr.fhir;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.domain.ParsedDiagnosis;
import org.openmrs.module.ihshr.utils.DiagnosisConstants;

public class DiagnosisConditionBuilder {
	
	public Condition build(Observation sourceObs, String obsUuid, ParsedDiagnosis parsedDiagnosis) {
		return build(sourceObs, obsUuid, parsedDiagnosis, null, null);
	}
	
	public Condition build(Observation sourceObs, String obsUuid, ParsedDiagnosis parsedDiagnosis,
	        Encounter sourceEncounter, Encounter parentEncounter) {
		Condition condition = new Condition();
		condition.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(DiagnosisConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(obsUuid);
		condition.addIdentifier(identifier);
		
		CodeableConcept category = new CodeableConcept();
		category.addCoding(new Coding().setSystem(DiagnosisConstants.CONDITION_CATEGORY_SYSTEM).setCode(
		    DiagnosisConstants.CONDITION_CATEGORY_CODE));
		condition.addCategory(category);
		
		String verificationCode = StringUtils.defaultIfBlank(parsedDiagnosis.getDiagnosisCategory(), "unconfirmed");
		condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.VERIFICATION_STATUS_SYSTEM).setCode(verificationCode)));
		
		condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    DiagnosisConstants.CLINICAL_STATUS_SYSTEM).setCode(DiagnosisConstants.CLINICAL_STATUS_CODE)));
		
		CodeableConcept code = new CodeableConcept();
		code.setText(parsedDiagnosis.getDiagnosisText());
		if (StringUtils.isNotBlank(parsedDiagnosis.getCode())) {
			code.addCoding(new Coding().setSystem(detectCodeSystem(parsedDiagnosis.getCode()))
			        .setCode(parsedDiagnosis.getCode()).setDisplay(parsedDiagnosis.getDiagnosisText()));
		} else {
			org.hl7.fhir.r4.model.Coding dictionaryCoding = ClinicalTermCodingResolver.lookupMapping(
			    parsedDiagnosis.getDiagnosisText(), null);
			if (dictionaryCoding != null) {
				code.addCoding(dictionaryCoding);
			}
		}
		condition.setCode(code);
		
		if (sourceObs != null) {
			if (sourceObs.hasSubject()) {
				condition.setSubject(sourceObs.getSubject().copy());
			}
			if (sourceObs.hasEncounter()) {
				condition.setEncounter(sourceObs.getEncounter().copy());
			}
			Reference asserter = resolveAsserter(sourceObs, sourceEncounter, parentEncounter);
			if (asserter != null) {
				condition.setAsserter(asserter);
			}
		}
		
		Date recorded = resolveRecordedDate(sourceObs);
		if (recorded != null) {
			condition.setRecordedDateElement(new DateTimeType(recorded));
		}
		
		return condition;
	}
	
	public static String detectCodeSystem(String code) {
		String c = StringUtils.trimToEmpty(code);
		if (c.matches("\\d{6,}")) {
			return DiagnosisConstants.SNOMED_SYSTEM;
		}
		if (c.matches("[A-Z]\\d+(\\.\\d+)?")) {
			return DiagnosisConstants.ICD10_SYSTEM;
		}
		return DiagnosisConstants.IH_NATIVE_CODE_SYSTEM;
	}
	
	static Reference resolveAsserter(Observation sourceObs, Encounter sourceEncounter, Encounter parentEncounter) {
		if (sourceObs != null && sourceObs.hasPerformer()) {
			return sourceObs.getPerformerFirstRep().copy();
		}
		Reference fromEncounter = EncounterPractitionerSupport.extractParticipantIndividualReference(sourceEncounter);
		if (fromEncounter != null) {
			return fromEncounter;
		}
		return EncounterPractitionerSupport.extractParticipantIndividualReference(parentEncounter);
	}
	
	private static Date resolveRecordedDate(Observation sourceObs) {
		if (sourceObs == null) {
			return null;
		}
		if (sourceObs.hasEffectiveDateTimeType()) {
			return sourceObs.getEffectiveDateTimeType().getValue();
		}
		if (sourceObs.hasIssued()) {
			return sourceObs.getIssued();
		}
		return null;
	}
}
