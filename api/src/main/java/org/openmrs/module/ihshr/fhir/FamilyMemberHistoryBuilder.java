package org.openmrs.module.ihshr.fhir;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.FamilyMemberHistory.FamilyMemberHistoryConditionComponent;
import org.hl7.fhir.r4.model.FamilyMemberHistory.FamilyHistoryStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.backlog.UnmappedTermArtifact;
import org.openmrs.module.ihshr.backlog.UnmappedTermBacklog;
import org.openmrs.module.ihshr.backlog.UnmappedTermLookupType;
import org.openmrs.module.ihshr.config.ShrLookupLoader;
import org.openmrs.module.ihshr.domain.ParsedFamilyHistoryRelative;
import org.openmrs.module.ihshr.utils.FamilyHistoryConstants;

public class FamilyMemberHistoryBuilder {
	
	public FamilyMemberHistory build(Observation source, String obsUuid, ParsedFamilyHistoryRelative relative,
	        String sharedNote) {
		FamilyMemberHistory history = new FamilyMemberHistory();
		history.setId((String) null);
		
		Identifier identifier = new Identifier();
		identifier.setSystem(FamilyHistoryConstants.IDENTIFIER_SYSTEM);
		identifier.setValue(FamilyHistoryConstants.familyMemberHistoryIdentifier(obsUuid, relative.getMapKey()));
		history.addIdentifier(identifier);
		
		history.setStatus(FamilyHistoryStatus.COMPLETED);
		
		CodeableConcept relationship = new CodeableConcept();
		if (StringUtils.isNotBlank(relative.getRoleCode())) {
			relationship.addCoding(new Coding().setSystem(FamilyHistoryConstants.ROLE_CODE_SYSTEM)
			        .setCode(relative.getRoleCode()).setDisplay(relative.getRelativeLabel()));
		}
		relationship.setText(relative.getRelativeLabel());
		history.setRelationship(relationship);
		
		for (String conditionText : relative.getConditions()) {
			FamilyMemberHistoryConditionComponent condition = history.addCondition();
			CodeableConcept code = new CodeableConcept();
			code.setText(conditionText);
			Coding snomed = ShrLookupLoader.lookupMapping("family-history-conditions.json", conditionText);
			if (snomed != null) {
				code.addCoding(snomed);
			} else {
				UnmappedTermBacklog.recordMiss(UnmappedTermArtifact.FAMILY_HISTORY_CONDITION,
				    "family-history-conditions.json", UnmappedTermLookupType.MAPPING, conditionText);
			}
			condition.setCode(code);
		}
		
		if (source != null) {
			if (source.hasSubject()) {
				history.setPatient(source.getSubject().copy());
			}
			Date recorded = source.getEffectiveDateTimeType() != null ? source.getEffectiveDateTimeType().getValue()
			        : (source.getIssued() != null ? source.getIssued() : null);
			if (recorded != null) {
				history.setDate(recorded);
			}
		}
		
		if (StringUtils.isNotBlank(sharedNote)) {
			history.addNote().setText(sharedNote);
		}
		
		return history;
	}
	
}
