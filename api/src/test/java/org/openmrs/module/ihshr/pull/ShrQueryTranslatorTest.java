package org.openmrs.module.ihshr.pull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;
import org.openmrs.module.ihshr.utils.CruidConstants;

public class ShrQueryTranslatorTest {
	
	@Test
	public void buildPatientResolveUrlEncodesCruidIdentifier() {
		String url = ShrQueryTranslator.buildPatientResolveUrl("CR-2026-000001");
		assertTrue(url.startsWith("Patient?"));
		assertTrue(url.contains("identifier="));
		assertTrue(url.contains(CruidConstants.IDENTIFIER_SYSTEM.replace(":", "%3A")));
		assertTrue(url.contains("CR-2026-000001"));
	}
	
	@Test
	public void defaultTimelineIncludesNoEchoIncludesAndRevincludes() {
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setIncludeLocalEcho(false);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("shr-patient-1", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.contains("subject=Patient%2Fshr-patient-1"));
		assertTrue(url.contains("_source%3Anot="));
		assertTrue(url.contains("_include=Encounter%3Aparticipant"));
		assertTrue(url.contains("_revinclude=Observation%3Aencounter"));
		assertTrue(url.contains("_revinclude=Provenance%3Atarget"));
	}
	
	@Test
	public void customMultiTypeBuildsOneQueryPerType() {
		java.util.Map<String, String> params = new java.util.HashMap<String, String>();
		params.put("view", "custom");
		params.put("recordTypes", "Encounter,Condition");
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("42", request);
		assertEquals(2, queries.size());
		assertEquals("Encounter", queries.get(0).getResourceType());
		assertEquals("Condition", queries.get(1).getResourceType());
	}
	
	@Test
	public void refreshEverythingUsesPatientOperation() {
		ShrFhirQuery query = ShrQueryTranslator.buildRefreshQuery("99", "2026-01-01T00:00:00Z", 25, false);
		assertTrue(query.getUrl().startsWith("Patient/99/$everything?"));
		assertTrue(query.getUrl().contains("_since=2026-01-01T00%3A00%3A00Z"));
		assertTrue(query.getUrl().contains("_source%3Anot="));
	}
	
	@Test
	public void followUpPresetUsesObservationConceptCode() {
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(java.util.Collections
		        .singletonMap("view", "follow_up"));
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("7", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.startsWith("Observation?"));
		assertTrue(url.contains("code=urn%3Aintelehealth%3Aopenmrs-concept%7C163345"));
		assertTrue(url.contains("_sort=-date"));
	}
	
	@Test
	public void referralsPresetUsesServiceRequestReferralIntent() {
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(java.util.Collections
		        .singletonMap("view", "referrals"));
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("7", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.startsWith("ServiceRequest?"));
		assertTrue(url.contains("category="));
		assertTrue(url.contains("urn%3Aintelehealth%3Aservice-request-type%7Creferral"));
		assertTrue(url.contains("_include=ServiceRequest%3Arequester"));
		assertTrue(url.contains("_include=ServiceRequest%3Aencounter"));
		assertTrue(url.contains("_sort=-authored"));
	}
	
	@Test
	public void problemsPresetUsesConditionSearch() {
		ShrHistoryRequest request = ShrHistoryRequest
		        .fromQueryParams(java.util.Collections.singletonMap("view", "problems"));
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("7", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.startsWith("Condition?"));
		assertTrue(url.contains("clinical-status=active"));
		assertTrue(url.contains("category=problem-list-item"));
	}
}
