package org.openmrs.module.ihshr.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.Test;

public class ReferralTransferTest {
	
	@Test
	public void build_urgentReferral_shouldProduceReferralIntentServiceRequest() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		ReferralBuildResult built = new ReferralTransfer().build(source, "obs-referral-1",
		    "Obstetrician & Gynecologist:PHC:Urgent:4ttr");
		
		assertEquals(1, built.totalResourceCount());
		ServiceRequest serviceRequest = built.getServiceRequests().get(0);
		assertEquals(ServiceRequest.ServiceRequestIntent.ORDER, serviceRequest.getIntent());
		assertEquals(ServiceRequest.ServiceRequestStatus.ACTIVE, serviceRequest.getStatus());
		assertEquals(ServiceRequest.ServiceRequestPriority.URGENT, serviceRequest.getPriority());
		assertEquals("Obstetrician & Gynecologist", serviceRequest.getCode().getText());
		assertEquals("referral", serviceRequest.getCategoryFirstRep().getCodingFirstRep().getCode());
		assertEquals("PHC", serviceRequest.getCategory().size() > 1 ? serviceRequest.getCategory().get(1).getText() : null);
		assertEquals("4ttr", serviceRequest.getNoteFirstRep().getText());
		assertEquals("obs-referral-1::ref-0", serviceRequest.getIdentifierFirstRep().getValue());
		assertNotNull(serviceRequest.getSubject());
		assertNotNull(serviceRequest.getEncounter());
	}
	
	@Test
	public void build_multilineReferral_shouldCreateIndexedServiceRequests() {
		Observation source = new Observation();
		source.setSubject(new Reference("Patient/test-patient"));
		source.setEncounter(new Reference("Encounter/test-encounter"));
		
		ReferralBuildResult built = new ReferralTransfer().build(source, "obs-referral-2",
		    "General Physician::Elective:sdasfa\nObstetrician & Gynecologist:PHC:Urgent:4ttr");
		
		assertEquals(2, built.totalResourceCount());
		assertEquals("obs-referral-2::ref-0", built.getServiceRequests().get(0).getIdentifierFirstRep().getValue());
		assertEquals("obs-referral-2::ref-1", built.getServiceRequests().get(1).getIdentifierFirstRep().getValue());
		assertTrue(built.getServiceRequests().get(0).getCategoryFirstRep().hasCoding());
	}
	
}
