package org.openmrs.module.ihshr.pull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	public void defaultTimelineIncludesIncludesAndRevincludes() {
		ShrHistoryRequest request = new ShrHistoryRequest();
		request.setIncludeLocalEcho(false);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("shr-patient-1", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.contains("patient.identifier=urn%3Aintelehealth%3Acruid%7Cshr-patient-1"));
		if (ShrPullSettings.sourceFilterEnabled()) {
			assertTrue(url.contains("_source%3Anot="));
		} else {
			assertFalse(url.contains("_source%3Anot="));
		}
		assertTrue(url.contains("_include=Encounter%3Aparticipant"));
		assertTrue(url.contains("_revinclude=Observation%3Aencounter"));
		assertTrue(url.contains("_revinclude=Provenance%3Atarget"));
	}
	
	@Test
	public void customEncounterTimelineOnlyRevincludesSelectedRecordTypes() {
		java.util.Map<String, String> params = new java.util.HashMap<String, String>();
		params.put("view", "custom");
		params.put("format", "timeline");
		params.put("recordTypes", "Encounter,Observation");
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("42", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.contains("class=AMB"));
		assertTrue(url.contains("_revinclude=Observation%3Aencounter"));
		assertFalse(url.contains("_revinclude=MedicationRequest%3Aencounter"));
		assertFalse(url.contains("_revinclude=Condition%3Aencounter"));
	}
	
	@Test
	public void customEncounterAndConditionUsesSingleEncounterQuery() {
		java.util.Map<String, String> params = new java.util.HashMap<String, String>();
		params.put("view", "custom");
		params.put("recordTypes", "Encounter,Condition");
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("42", request);
		assertEquals(1, queries.size());
		assertEquals("Encounter", queries.get(0).getResourceType());
		assertTrue(queries.get(0).getUrl().contains("_revinclude=Condition%3Aencounter"));
	}
	
	@Test
	public void customWithoutRecordTypesDefaultsToAllTypes() {
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(java.util.Collections.singletonMap("view", "custom"));
		assertEquals(ShrPullRecordType.defaultCustomTypes().size(), ShrRecordTypeSelection.resolved(request).size());
	}
	
	@Test
	public void customMultiTypeBuildsOneQueryPerNonBundledType() {
		java.util.Map<String, String> params = new java.util.HashMap<String, String>();
		params.put("view", "custom");
		params.put("recordTypes", "Encounter,FamilyMemberHistory");
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("42", request);
		assertEquals(2, queries.size());
		assertEquals("Encounter", queries.get(0).getResourceType());
		assertEquals("FamilyMemberHistory", queries.get(1).getResourceType());
	}
	
	@Test
	public void refreshEverythingUsesPatientOperation() {
		ShrFhirQuery query = ShrQueryTranslator.buildRefreshQuery("99", "2026-01-01T00:00:00Z", 25, false);
		assertTrue(query.getUrl().startsWith("Encounter?"));
		assertTrue(query.getUrl().contains("patient.identifier=urn%3Aintelehealth%3Acruid%7C99"));
		assertTrue(query.getUrl().contains("_lastUpdated=ge2026-01-01T00%3A00%3A00Z"));
		if (ShrPullSettings.sourceFilterEnabled()) {
			assertTrue(query.getUrl().contains("_source%3Anot="));
		} else {
			assertFalse(query.getUrl().contains("_source%3Anot="));
		}
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
		assertTrue(url.contains("intent=referral"));
		assertTrue(url.contains("category="));
		assertTrue(url.contains("urn%3Aintelehealth%3Aservice-request-type%7Creferral"));
		assertTrue(url.contains("_include=ServiceRequest%3Arequester"));
		assertTrue(url.contains("_include=ServiceRequest%3Aencounter"));
		assertTrue(url.contains("_sort=-authored"));
	}
	
	@Test
	public void vitalsPresetDefaultsToAscendingSort() {
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(java.util.Collections.singletonMap("view", "vitals"));
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("7", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.contains("_sort=date"));
		assertFalse(url.contains("_sort=-date"));
	}
	
	@Test
	public void labsPresetUsesCompositeSearch() {
		java.util.Map<String, String> params = new java.util.HashMap<String, String>();
		params.put("view", "labs");
		params.put("labCode", "2339-0");
		params.put("labValue", "180");
		ShrHistoryRequest request = ShrHistoryRequest.fromQueryParams(params);
		List<ShrFhirQuery> queries = ShrQueryTranslator.buildHistoryQueries("7", request);
		assertEquals(1, queries.size());
		String url = queries.get(0).getUrl();
		assertTrue(url.startsWith("Observation?"));
		assertTrue(url.contains("category=laboratory"));
		assertTrue(url.contains("code-value-quantity="));
		assertTrue(url.contains("2339-0"));
		assertTrue(url.contains("%24gt180"));
		assertTrue(url.contains("_sort=date"));
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
