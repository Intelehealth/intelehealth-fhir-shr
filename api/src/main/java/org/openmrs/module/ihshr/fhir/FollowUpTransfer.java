package org.openmrs.module.ihshr.fhir;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.ihshr.domain.ParsedFollowUp;
import org.openmrs.module.ihshr.parser.FollowUpParser;

public class FollowUpTransfer {
	
	private final FollowUpParser parser = new FollowUpParser();
	
	private final FollowUpObservationBuilder builder = new FollowUpObservationBuilder();
	
	public FollowUpBuildResult build(Observation sourceObs, String obsUuid, String valueText) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid)) {
			return empty();
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
		}
		if (StringUtils.isBlank(valueText) || FollowUpParser.isDenied(valueText)) {
			return empty();
		}
		ParsedFollowUp parsed = parser.parse(valueText);
		if (parsed == null) {
			return empty();
		}
		return new FollowUpBuildResult(builder.build(sourceObs, obsUuid, parsed));
	}
	
	private static FollowUpBuildResult empty() {
		return new FollowUpBuildResult(null);
	}
	
}
