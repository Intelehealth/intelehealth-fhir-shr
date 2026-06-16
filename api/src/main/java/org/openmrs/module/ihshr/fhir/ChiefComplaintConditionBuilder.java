package org.openmrs.module.ihshr.fhir;

import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.config.ClinicalTermCodingResolver;
import org.openmrs.module.ihshr.domain.ParsedComplaint;
import org.openmrs.module.ihshr.utils.ChiefComplaintConstants;

/**
 * Maps one parsed chief-complaint symptom onto a FHIR R4 {@link Condition} for SHR push. Used by
 * {@link ChiefComplaintTransfer}; multiple Conditions may be produced from a single obs when the UI
 * lists several {@code ►<b>Symptom</b>} blocks.
 */
public class ChiefComplaintConditionBuilder {
	
	private static final String CHIEF_COMPLAINT_MAPPINGS = "chief-complaint-mappings.json";
	
	public Condition build(Observation source, String obsUuid, ParsedComplaint complaint) {
		Condition condition = new Condition();
		condition.setId((String) null);
		
		// Stable id per symptom index within the same obs (obsUuid::cc-0, ::cc-1, …).
		Identifier identifier = new Identifier();
		identifier.setSystem(ChiefComplaintConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(ChiefComplaintConstants.conditionIdentifier(obsUuid, complaint.getIndex()));
		condition.addIdentifier(identifier);
		
		CodeableConcept category = new CodeableConcept();
		category.addCoding(new Coding().setSystem(ChiefComplaintConstants.CONDITION_CATEGORY_SYSTEM).setCode(
		    ChiefComplaintConstants.CONDITION_CATEGORY_CODE));
		condition.addCategory(category);
		
		condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    ChiefComplaintConstants.VERIFICATION_STATUS_SYSTEM).setCode(ChiefComplaintConstants.VERIFICATION_STATUS_CODE)));
		
		condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding().setSystem(
		    ChiefComplaintConstants.CLINICAL_STATUS_SYSTEM).setCode(ChiefComplaintConstants.CLINICAL_STATUS_CODE)));
		
		CodeableConcept code = new CodeableConcept();
		code.setText(complaint.getSymptom());
		// Layer 1 OpenMRS concept map, then chief-complaint-mappings.json; code.text is always kept.
		Coding snomed = ClinicalTermCodingResolver.resolveMapping(complaint.getSymptom(), CHIEF_COMPLAINT_MAPPINGS,
		    UnmappedTermArtifact.CHIEF_COMPLAINT);
		if (snomed != null) {
			code.addCoding(snomed);
		}
		condition.setCode(code);
		
		if (complaint.getOnsetDateTime() != null) {
			condition.setOnset(new DateTimeType(complaint.getOnsetDateTime()));
		}
		
		if (source != null) {
			if (source.hasSubject()) {
				condition.setSubject(source.getSubject().copy());
			}
			if (source.hasEncounter()) {
				condition.setEncounter(source.getEncounter().copy());
			}
			if (source.hasPerformer()) {
				condition.setAsserter(source.getPerformerFirstRep().copy());
			}
			Date recorded = source.getEffectiveDateTimeType() != null ? source.getEffectiveDateTimeType().getValue()
			        : (source.getIssued() != null ? source.getIssued() : null);
			if (recorded != null) {
				condition.setRecordedDate(recorded);
			}
		}
		
		// Full stripped obs text is duplicated on each Condition when multiple symptoms share one obs.
		if (StringUtils.isNotBlank(complaint.getSharedNoteText())) {
			condition.addNote().setText(complaint.getSharedNoteText());
		}
		
		return condition;
	}
	
}
