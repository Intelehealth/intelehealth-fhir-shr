package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.module.ihshr.domain.ParsedReferral;
import org.openmrs.module.ihshr.parser.ReferralParser;

/**
 * Builds SHR-ready referral {@link ServiceRequest} resources from one OpenMRS obs (concept 165238).
 * Called by {@code DataSendToSHR.addReferralToVisitBuilder} while
 * {@link org.openmrs.module.ihshr.backlog.UnmappedTermBacklogContext} is populated for Layer 1/2
 * specialty coding misses.
 * <p>
 * Examples (from {@link ReferralParser}; one obs may contain several lines):
 * <ul>
 * <li>Elective: {@code General Physician::Elective:sdasfa} → specialty, category, notes.</li>
 * <li>Urgent: {@code Obstetrician & Gynecologist:PHC:Urgent:4ttr} → specialty, category, priority,
 * notes.</li>
 * <li>Multi-referral: {@code General Physician::Elective:sdasfa<br/>
 * Obstetrician & Gynecologist:PHC:Urgent:4ttr} → two {@link ServiceRequest}s ({@code obsUuid::ref-0}, {@code ::ref-1}).</li>
 * <li>JSON wrapper: {@code "en":"General Physician::Elective:sdasfa"} .</li>
 * </ul>
 */
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
			// One ORDER ServiceRequest per referral line; index becomes identifier suffix (::ref-0, ::ref-1, …).
			serviceRequests.add(builder.build(sourceObs, obsUuid, parsed));
		}
		return new ReferralBuildResult(serviceRequests);
	}
	
	private static ReferralBuildResult empty() {
		return new ReferralBuildResult(Collections.<ServiceRequest> emptyList());
	}
	
}
