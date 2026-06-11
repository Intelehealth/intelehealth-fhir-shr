package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.domain.ParsedDiagnosis;
import org.openmrs.module.ihshr.parser.DiagnosisParser;

public class DiagnosisTransfer {
	
	private final DiagnosisParser parser = new DiagnosisParser();
	
	private final DiagnosisConditionBuilder builder = new DiagnosisConditionBuilder();
	
	public DiagnosisBuildResult build(Observation sourceObs, String obsUuid, String valueText, int unknownRankBase) {
		return build(sourceObs, obsUuid, valueText, unknownRankBase, null, null);
	}
	
	public DiagnosisBuildResult build(Observation sourceObs, String obsUuid, String valueText, int unknownRankBase,
	        Encounter sourceEncounter, Encounter parentEncounter) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid) || StringUtils.isBlank(valueText)) {
			return null;
		}
		ParsedDiagnosis parsed = parser.parse(valueText);
		if (parsed == null || StringUtils.isBlank(parsed.getDiagnosisText())) {
			return null;
		}
		Condition condition = builder.build(sourceObs, obsUuid, parsed, sourceEncounter, parentEncounter);
		Integer rank = resolveRank(parsed, unknownRankBase);
		return new DiagnosisBuildResult(condition, rank);
	}
	
	private Integer resolveRank(ParsedDiagnosis parsed, int unknownRankBase) {
		if ("Primary".equals(parsed.getDiagnosisType())) {
			return Integer.valueOf(1);
		}
		if ("Secondary".equals(parsed.getDiagnosisType())) {
			return Integer.valueOf(2);
		}
		return Integer.valueOf(unknownRankBase);
	}
}
