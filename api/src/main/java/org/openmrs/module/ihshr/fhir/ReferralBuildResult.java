package org.openmrs.module.ihshr.fhir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.ServiceRequest;

public class ReferralBuildResult {
	
	private final List<ServiceRequest> serviceRequests;
	
	public ReferralBuildResult(List<ServiceRequest> serviceRequests) {
		this.serviceRequests = serviceRequests == null ? Collections.<ServiceRequest> emptyList()
		        : new ArrayList<ServiceRequest>(serviceRequests);
	}
	
	public List<ServiceRequest> getServiceRequests() {
		return serviceRequests;
	}
	
	public int totalResourceCount() {
		return serviceRequests.size();
	}
	
}
