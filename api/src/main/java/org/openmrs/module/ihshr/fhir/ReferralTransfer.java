package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.domain.ParsedReferral;
import org.openmrs.module.ihshr.parser.ReferralParser;

public class ReferralTransfer {
	
	private final ReferralParser parser = new ReferralParser();
	
	private final ReferralServiceRequestBuilder builder = new ReferralServiceRequestBuilder();
	
	public ReferralBuildResult build(Observation sourceObs, String obsUuid, String valueText) {
		if (sourceObs == null || StringUtils.isBlank(obsUuid)) {
			return empty();
		}
		if (StringUtils.isBlank(valueText) && sourceObs.hasValueStringType()) {
			valueText = sourceObs.getValueStringType().getValueAsString();
		}
		if (StringUtils.isBlank(valueText)) {
			return empty();
		}
		List<ParsedReferral> parsedReferrals = parser.parse(valueText);
		if (parsedReferrals.isEmpty()) {
			return empty();
		}
		List<ServiceRequest> serviceRequests = new ArrayList<ServiceRequest>();
		for (ParsedReferral parsed : parsedReferrals) {
			serviceRequests.add(builder.build(sourceObs, obsUuid, parsed));
		}
		return new ReferralBuildResult(serviceRequests);
	}
	
	private static ReferralBuildResult empty() {
		return new ReferralBuildResult(Collections.<ServiceRequest> emptyList());
	}
	
}
